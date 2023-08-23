package bio.terra.workspace.service.workspace.flight.delete.workspace;

import static java.lang.Boolean.TRUE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;

/** Step to delete the workspace metadata */
public class DeleteWorkspaceStartStep implements Step {
  private final UUID workspaceUuid;
  private final WorkspaceDao workspaceDao;

  public DeleteWorkspaceStartStep(UUID workspaceUuid, WorkspaceDao workspaceDao) {
    this.workspaceUuid = workspaceUuid;
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    workspaceDao.deleteWorkspaceStart(workspaceUuid, flightContext.getFlightId());
    flightContext
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_STATE_CHANGED, TRUE);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // If we successfully changed state, then we assume that something bad happened
    // during delete processing, and we made it to this step because all UNDO
    // processing was successful. We return the resource to the READY state.
    // It is unclear that this ever happens - failures on delete typically lead
    // to dismal failures - the resource will be stuck in a DELETING state -
    // and we will have to do a manual intervention. However, being conservative,
    // there may be recoverable delete cases, so we handle them this way.
    var resourceStateChanged =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_STATE_CHANGED, Boolean.class);
    if (TRUE.equals(resourceStateChanged)) {
      workspaceDao.deleteWorkspaceFailure(workspaceUuid, flightContext.getFlightId());
    }
    return StepResult.getStepResultSuccess();
  }
}
