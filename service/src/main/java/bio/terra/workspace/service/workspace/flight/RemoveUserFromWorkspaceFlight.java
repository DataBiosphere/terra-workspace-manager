package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
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
    String userToRemove = inputParameters.get(WorkspaceFlightMapKeys.USER_TO_REMOVE, String.class);
    Optional<WsmIamRole> roleToRemove =
        Optional.ofNullable(
            WsmIamRole.valueOf(
                inputParameters.get(WorkspaceFlightMapKeys.ROLE_TO_REMOVE, String.class)));

    // Flight plan:
    // 0. (Pre-flight): Validate that the user is directly granted the specified workspace role.
    //  WSM does not manage groups, so users with indirect group-based access cannot be removed
    //  via this flight.
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
            workspaceUuid,
            roleToRemove.orElse(null),
            userToRemove,
            appContext.getSamService(),
            userRequest),
        samRetry);
    addStep(
        new CheckUserStillInWorkspaceStep(
            workspaceUuid, userToRemove, appContext.getSamService(), userRequest),
        samRetry);
    addStep(
        new ClaimUserPrivateResourcesStep(
            workspaceUuid,
            userToRemove,
            appContext.getResourceDao(),
            appContext.getSamService(),
            userRequest),
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
            userRequest),
        RetryRules.cloud());
    addStep(
        new ReleasePrivateResourceCleanupClaimsStep(
            workspaceUuid, userToRemove, appContext.getResourceDao()),
        dbRetry);
  }
}
