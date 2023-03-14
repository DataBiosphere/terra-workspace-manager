package bio.terra.workspace.service.resource.controlled.flight.delete;

import static java.lang.Boolean.TRUE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting the metadata that WSM stores for a controlled resource. */
public class DeleteMetadataStartStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  private final Logger logger = LoggerFactory.getLogger(DeleteMetadataStartStep.class);

  public DeleteMetadataStartStep(ResourceDao resourceDao, UUID workspaceUuid, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // State transition to deleting; or failure if another flight is using the resource
    resourceDao.deleteResourceStart(workspaceUuid, resourceId, flightContext.getFlightId());
    flightContext
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_STATE_CHANGED, TRUE);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // If we successfully changed state, then we assume that something bad happened
    // during delete processing and we made it to this step because all UNDO
    // processing was successful. We return the resource to the READY state.
    // It is unclear that this ever happens - failures on delete typically lead
    // to dismal failures - the resource will be stuck in a DELETING state
    // and we will have to do a manual intervention. However, being conservative,
    // there may be recoverable delete cases, so we handle them this way.
    var resourceStateChanged =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_STATE_CHANGED, Boolean.class);
    if (TRUE.equals(resourceStateChanged)) {
      resourceDao.deleteResourceFailure(
          workspaceUuid,
          resourceId,
          flightContext.getFlightId(),
          flightContext.getResult().getException().orElse(null));
    }

    return flightContext.getResult();
  }
}
