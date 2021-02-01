package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public class DeleteWorkspaceStateStep implements Step {

  private final WorkspaceDao workspaceDao;

  @Autowired
  public DeleteWorkspaceStateStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    // WorkspaceDao.deleteWorkspace returns true if a delete succeeds or false if the workspace is
    // not found, but the user-facing delete operation should return a 204 even if the workspace is
    // not found.
    workspaceDao.deleteWorkspace(workspaceID);
    FlightUtils.setResponse(flightContext, null, HttpStatus.valueOf(204));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We can't really undo a state delete: deleting the workspace cascades to multiple other
    // state tables, and this undo can't re-create all that state.
    // This should absolutely be the last step of a flight, and because we're unable to undo it
    // we simply return a fatal status.
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
