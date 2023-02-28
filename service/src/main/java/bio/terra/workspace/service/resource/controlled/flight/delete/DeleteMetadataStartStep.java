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
    // during delete processing and call this a broken delete.
    // Otherwise, we assume it failed because the resource is busy and simply return.
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
