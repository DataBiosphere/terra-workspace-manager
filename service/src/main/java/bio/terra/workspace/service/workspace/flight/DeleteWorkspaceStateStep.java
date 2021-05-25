package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class DeleteWorkspaceStateStep implements Step {

  private final WorkspaceDao workspaceDao;
  private final UUID workspaceId;

  private final Logger logger = LoggerFactory.getLogger(DeleteWorkspaceStateStep.class);

  public DeleteWorkspaceStateStep(WorkspaceDao workspaceDao, UUID workspaceId) {
    this.workspaceDao = workspaceDao;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    // WorkspaceDao.deleteWorkspace returns true if a delete succeeds or false if the workspace is
    // not found, but the user-facing delete operation should return a 204 even if the workspace is
    // not found.
    workspaceDao.deleteWorkspace(workspaceId);
    FlightUtils.setResponse(flightContext, null, HttpStatus.NO_CONTENT);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We can't really undo a state delete: deleting the workspace cascades to multiple other
    // state tables, and this undo can't re-create all that state.
    // This should absolutely be the last step of a flight, and because we're unable to undo it
    // we surface the error from the DO step that led to this issue.
    FlightMap inputMap = flightContext.getInputParameters();
    logger.error("Unable to undo deletion of workspace {} in WSM DB", workspaceId);
    return flightContext.getResult();
  }
}
