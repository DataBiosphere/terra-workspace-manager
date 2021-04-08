package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import java.util.UUID;

/**
 * A {@code Step} which synchronizes Sam policies with google groups and stores the group names in
 * the Stairway working map.
 */
public class SyncSamGroupsStep implements Step {

  private final SamService samService;

  public SyncSamGroupsStep(SamService samService) {
    this.samService = samService;
  }

  // Note that the SamService.syncWorkspacePolicy is already idempotent, so this doesn't need to
  // be explicitly handled here.
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    UUID workspaceId =
        UUID.fromString(
            flightContext
                .getInputParameters()
                .get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userReq =
        flightContext
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(
        WorkspaceFlightMapKeys.IAM_OWNER_GROUP_EMAIL,
        samService.syncWorkspacePolicy(workspaceId, WsmIamRole.OWNER, userReq));
    workingMap.put(
        WorkspaceFlightMapKeys.IAM_APPLICATION_GROUP_EMAIL,
        samService.syncWorkspacePolicy(workspaceId, WsmIamRole.APPLICATION, userReq));
    workingMap.put(
        WorkspaceFlightMapKeys.IAM_WRITER_GROUP_EMAIL,
        samService.syncWorkspacePolicy(workspaceId, WsmIamRole.WRITER, userReq));
    workingMap.put(
        WorkspaceFlightMapKeys.IAM_READER_GROUP_EMAIL,
        samService.syncWorkspacePolicy(workspaceId, WsmIamRole.READER, userReq));

    return StepResult.getStepResultSuccess();
  }

  // Sam policies are never "de-synced" from google groups, so there's nothing to undo here.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
