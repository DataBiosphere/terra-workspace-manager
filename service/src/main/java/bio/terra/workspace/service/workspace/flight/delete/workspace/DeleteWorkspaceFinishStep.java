package bio.terra.workspace.service.workspace.flight.delete.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.WorkspaceDao;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting the metadata that WSM stores for a controlled resource. */
public class DeleteWorkspaceFinishStep implements Step {
  private final UUID workspaceUuid;
  private final WorkspaceDao workspaceDao;

  private final Logger logger = LoggerFactory.getLogger(DeleteWorkspaceFinishStep.class);

  public DeleteWorkspaceFinishStep(UUID workspaceUuid, WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    workspaceDao.deleteWorkspaceSuccess(workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InternalLogicException("Cannot undo delete of WSM workspace " + workspaceUuid));
  }
}
