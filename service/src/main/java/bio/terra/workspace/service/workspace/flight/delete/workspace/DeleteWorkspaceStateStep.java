package bio.terra.workspace.service.workspace.flight.delete.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.WorkspaceDao;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteWorkspaceStateStep implements Step {

  private final WorkspaceDao workspaceDao;
  private final UUID workspaceUuid;

  private final Logger logger = LoggerFactory.getLogger(DeleteWorkspaceStateStep.class);

  public DeleteWorkspaceStateStep(WorkspaceDao workspaceDao, UUID workspaceUuid) {
    this.workspaceDao = workspaceDao;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws RetryException, InterruptedException {
    workspaceDao.deleteWorkspace(workspaceUuid);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We can't really undo a state delete: deleting the workspace cascades to multiple other
    // state tables, and this undo can't re-create all that state.
    // This should absolutely be the last step of a flight, and because we're unable to undo it
    // we surface the error from the DO step that led to this issue.
    logger.error("Unable to undo deletion of workspace {} in WSM DB", workspaceUuid);
    return flightContext.getResult();
  }
}
