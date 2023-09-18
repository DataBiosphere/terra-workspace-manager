package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.removeuser.CheckWorkspaceUserActionsStep;
import bio.terra.workspace.service.workspace.flight.removeuser.ClaimUserPrivateResourcesStep;
import bio.terra.workspace.service.workspace.flight.removeuser.MarkPrivateResourcesAbandonedStep;
import bio.terra.workspace.service.workspace.flight.removeuser.ReleasePrivateResourceCleanupClaimsStep;
import bio.terra.workspace.service.workspace.flight.removeuser.RemovePrivateResourceAccessStep;
import bio.terra.workspace.service.workspace.flight.removeuser.RemoveUserFromSamStep;
import bio.terra.workspace.service.workspace.flight.removeuser.ValidateUserRoleStep;
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
    // 1. Validate the user directly has the specified role in this workspace. Users may have a role
    //  via group membership, and WSM cannot remove this. Additionally, validate that a user is not
    //  removing themselves as the sole owner of a workspace.
    // 2. Remove role from user, if one is specified. This flight also runs periodically to clean up
    // abandoned private resources, in which case the user is already out of the workspace.
    // 3. Check with Sam whether the user can still read or write the workspace.
    // 4. Build a list of their private resources that should have access removed based on their
    // current access.
    // 5. In subflights, remove native access to those resources.
    // 6. Remove the user from all roles in sam on those private resources.
    // 7. Revoke the user's permission to use their pet SA in this workspace.
    RetryRule samRetry = RetryRules.shortExponential();
    RetryRule dbRetry = RetryRules.shortDatabase();
    if (roleToRemove != null) {
      addStep(
          new ValidateUserRoleStep(
              workspaceUuid, roleToRemove, userToRemove, samService, userRequest),
          samRetry);
      addStep(
          new RemoveUserFromSamStep(
              workspaceUuid, roleToRemove, userToRemove, samService, userRequest),
          samRetry);
    }
    // From this point on, if the user is removing themselves from the workspace, their userRequest
    // may no longer have permissions in Sam. To handle this, all later steps use WSM's credentials
    // instead.
    addStep(new CheckWorkspaceUserActionsStep(workspaceUuid, userToRemove, samService), samRetry);
    addStep(
        new ClaimUserPrivateResourcesStep(
            workspaceUuid, userToRemove, appContext.getResourceDao(), samService, wsmSaRequest),
        samRetry);
    addStep(new MakeFlightIdsStep(), dbRetry);
    addStep(new RemoveNativeAccessToPrivateResourcesStep(), dbRetry);
    addStep(new RemovePrivateResourceAccessStep(userToRemove, samService), samRetry);
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
