package bio.terra.workspace.service.workspace.flight.gcp;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.CheckUserStillInWorkspaceStep;
import bio.terra.workspace.service.workspace.flight.ClaimUserPrivateResourcesStep;
import bio.terra.workspace.service.workspace.flight.MarkPrivateResourcesAbandonedStep;
import bio.terra.workspace.service.workspace.flight.ReleasePrivateResourceCleanupClaimsStep;
import bio.terra.workspace.service.workspace.flight.RemovePrivateResourceAccessStep;
import bio.terra.workspace.service.workspace.flight.RemoveUserFromSamStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.Optional;
import java.util.UUID;

public class RemoveUserFromWorkspaceFlight extends Flight {

  public RemoveUserFromWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    UUID workspaceUuid =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    SamService samService = appContext.getSamService();
    String wsmSaToken = samService.getWsmServiceAccountToken();
    AuthenticatedUserRequest wsmSaRequest =
        new AuthenticatedUserRequest().token(Optional.of(wsmSaToken));
    String userToRemove = inputParameters.get(WorkspaceFlightMapKeys.USER_TO_REMOVE, String.class);
    WsmIamRole roleToRemove =
        inputParameters.get(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, String.class) != null
            ? WsmIamRole.valueOf(
                inputParameters.get(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, String.class))
            : null;

    // Flight plan:
    // 0. (Pre-flight): Validate that the user is directly granted the specified workspace role.
    //  WSM does not manage groups, so users with indirect group-based access cannot be removed
    //  via this flight.
    // 0. (Pre-flight): Validate that the user is not removing themselves as the only owner. WSM
    //  does not allow users to abandon workspaces this way.
    // 1. Remove role from user, if one is specified. This flight also runs periodically to clean up
    // abandoned private resources, in which case the user is already out of the workspace.
    // 2. Check with Sam whether the user is still in the workspace (i.e. can still read in the
    //  workspace) via other roles or groups. If so, we do not need to clean up their private
    //  resources, and can skip the rest of the flight.
    // 3. If the user is fully removed from the workspace, build a list of their private resources
    //  by reading WSM's DB.
    // 4. Remove the user from all roles on those private resources.
    // 5. Revoke the user's permission to use their pet SA in this workspace.
    RetryRule samRetry = RetryRules.shortExponential();
    RetryRule dbRetry = RetryRules.shortDatabase();
    addStep(
        new RemoveUserFromSamStep(
            workspaceUuid, roleToRemove, userToRemove, appContext.getSamService(), userRequest),
        samRetry);
    addStep(
        new CheckUserStillInWorkspaceStep(workspaceUuid, userToRemove, appContext.getSamService()),
        samRetry);
    addStep(
        new ClaimUserPrivateResourcesStep(
            workspaceUuid,
            userToRemove,
            appContext.getResourceDao(),
            appContext.getSamService(),
            wsmSaRequest),
        samRetry);
    addStep(
        new RemovePrivateResourceAccessStep(userToRemove, appContext.getSamService()), samRetry);
    addStep(
        new MarkPrivateResourcesAbandonedStep(
            workspaceUuid, userToRemove, appContext.getResourceDao()),
        dbRetry);
    addStep(
        new RevokePetUsagePermissionStep(
            workspaceUuid,
            userToRemove,
            appContext.getPetSaService(),
            appContext.getGcpCloudContextService(),
            wsmSaRequest),
        RetryRules.cloud());
    addStep(
        new ReleasePrivateResourceCleanupClaimsStep(
            workspaceUuid, userToRemove, appContext.getResourceDao()),
        dbRetry);
  }
}
