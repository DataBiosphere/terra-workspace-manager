package bio.terra.workspace.service.workspace.flight.delete.cloudcontext;

import static java.lang.Boolean.TRUE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

/**
 * Completes the cloud context deletion by removing the row from the database, provided it is in the
 * proper state and owned by this flight.
 */
public class DeleteCloudContextStartStep implements Step {
  private final UUID workspaceUuid;
  private final WorkspaceDao workspaceDao;
  private final CloudPlatform cloudPlatform;

  public DeleteCloudContextStartStep(
      UUID workspaceUuid, WorkspaceDao workspaceDao, CloudPlatform cloudPlatform) {
    this.workspaceUuid = workspaceUuid;
    this.workspaceDao = workspaceDao;
    this.cloudPlatform = cloudPlatform;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    workspaceDao.deleteCloudContextStart(workspaceUuid, cloudPlatform, flightContext.getFlightId());
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
      workspaceDao.deleteCloudContextFailure(
          workspaceUuid, cloudPlatform, flightContext.getFlightId());
    }
    return StepResult.getStepResultSuccess();
  }
}
