package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

/**
 * Save a copy of the metadata before update so that the update can be restored on the undo path.
 * Uses the working map of the flight
 */
public class RetrieveControlledResourceMetadataStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final UUID resourceId;

  public RetrieveControlledResourceMetadataStep(
      ResourceDao resourceDao, UUID workspaceId, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    WsmResource resource = resourceDao.getResource(workspaceId, resourceId);
    if (resource.getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new IllegalArgumentException(
          String.format(
              "Resource %s in workspace %s is not a controlled resource.",
              resourceId.toString(), workspaceId.toString()));
    }
    ControlledResource controlledResource = resource.castToControlledResource();

    flightContext
        .getWorkingMap()
        .put(ControlledResourceKeys.PREVIOUS_RESOURCE_NAME, controlledResource.getName());
    flightContext
        .getWorkingMap()
        .put(
            ControlledResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            controlledResource.getDescription());
    return StepResult.getStepResultSuccess();
  }

  /**
   * There are no side effects to undo here. We could delete the entries from the working map, but
   * no earlier steps should be looking for them, so it's OK to let them dangle.
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
