package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.util.HashMap;
import java.util.UUID;

/**
 * A {@code Step} which synchronizes Sam policies with google groups and stores the group names in
 * the Stairway working map.
 */
public class SyncSamGroupsStep implements Step {

  private final SamService samService;
  private final UUID workspaceId;
  private final AuthenticatedUserRequest userReq;

  public SyncSamGroupsStep(
      SamService samService, UUID workspaceId, AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.workspaceId = workspaceId;
    this.userReq = userReq;
  }

  // Note that the SamService.syncWorkspacePolicy is already idempotent, so this doesn't need to
  // be explicitly handled here.
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // This cannot be an ImmutableMap, as those do not deserialize properly with Jackson.
    var workspaceRoleGroupMap = new HashMap<WsmIamRole, String>();
    workspaceRoleGroupMap.put(
        WsmIamRole.OWNER, samService.syncWorkspacePolicy(workspaceId, WsmIamRole.OWNER, userReq));
    workspaceRoleGroupMap.put(
        WsmIamRole.APPLICATION,
        samService.syncWorkspacePolicy(workspaceId, WsmIamRole.APPLICATION, userReq));
    workspaceRoleGroupMap.put(
        WsmIamRole.WRITER, samService.syncWorkspacePolicy(workspaceId, WsmIamRole.WRITER, userReq));
    workspaceRoleGroupMap.put(
        WsmIamRole.READER, samService.syncWorkspacePolicy(workspaceId, WsmIamRole.READER, userReq));

    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, workspaceRoleGroupMap);

    return StepResult.getStepResultSuccess();
  }

  // Sam policies are never "de-synced" from google groups, so there's nothing to undo here.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
