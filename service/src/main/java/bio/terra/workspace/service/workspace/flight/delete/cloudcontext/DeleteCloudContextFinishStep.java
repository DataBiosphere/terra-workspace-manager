package bio.terra.workspace.service.workspace.flight.delete.cloudcontext;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.UUID;

/** A step for deleting the metadata that WSM stores for a controlled resource. */
public class DeleteCloudContextFinishStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final UUID workspaceUuid;
  private final WorkspaceDao workspaceDao;
  private final CloudPlatform cloudPlatform;

  private final WorkspaceActivityLogService workspaceActivityLogService;

  public DeleteCloudContextFinishStep(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      WorkspaceDao workspaceDao,
      CloudPlatform cloudPlatform,
      WorkspaceActivityLogService workspaceActivityLogService) {
    this.userRequest = userRequest;
    this.workspaceDao = workspaceDao;
    this.workspaceUuid = workspaceUuid;
    this.cloudPlatform = cloudPlatform;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    workspaceDao.deleteCloudContextSuccess(
        workspaceUuid, cloudPlatform, flightContext.getFlightId());
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.DELETE,
        workspaceUuid.toString(),
        cloudPlatform.toActivityLogChangeTarget());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InternalLogicException(
            String.format(
                "Cannot undo delete of WSM %s cloud context in workspace %s.",
                cloudPlatform, workspaceUuid)));
  }
}
