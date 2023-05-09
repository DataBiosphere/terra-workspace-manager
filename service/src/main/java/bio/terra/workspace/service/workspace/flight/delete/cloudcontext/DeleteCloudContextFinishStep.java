package bio.terra.workspace.service.workspace.flight.delete.cloudcontext;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting the metadata that WSM stores for a controlled resource. */
public class DeleteCloudContextFinishStep implements Step {
  private final UUID workspaceUuid;
  private final WorkspaceDao workspaceDao;
  private final CloudPlatform cloudPlatform;

  private final Logger logger = LoggerFactory.getLogger(DeleteCloudContextFinishStep.class);

  public DeleteCloudContextFinishStep(
      UUID workspaceUuid, WorkspaceDao workspaceDao, CloudPlatform cloudPlatform) {
    this.workspaceDao = workspaceDao;
    this.workspaceUuid = workspaceUuid;
    this.cloudPlatform = cloudPlatform;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    workspaceDao.deleteCloudContextSuccess(
        workspaceUuid, cloudPlatform, flightContext.getFlightId());
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
