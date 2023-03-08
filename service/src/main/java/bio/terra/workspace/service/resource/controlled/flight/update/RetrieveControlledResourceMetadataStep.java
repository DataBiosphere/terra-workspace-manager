package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

/**
 * For Controlled Resource clone, save a copy of the metadata before update so that the update can
 * be restored on the undo path. Uses the working map of the flight.
 *
 * <p>Preconditions: Source resource exists in the DAO and is a Controlled Resource.
 *
 * <p>Post conditions: Working map has PREVIOUS_RESOURCE_NAME and PREVIOUS_RESOURCE_DESCRIPTION
 * populated.
 */
public class RetrieveControlledResourceMetadataStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  public RetrieveControlledResourceMetadataStep(
      ResourceDao resourceDao, UUID workspaceUuid, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);
    ControlledResource controlledResource = resource.castToControlledResource();

    flightContext
        .getWorkingMap()
        .put(ResourceKeys.PREVIOUS_RESOURCE_NAME, controlledResource.getName());
    flightContext
        .getWorkingMap()
        .put(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, controlledResource.getDescription());
    flightContext
        .getWorkingMap()
        .put(
            ResourceKeys.PREVIOUS_CLONING_INSTRUCTIONS,
            controlledResource.getCloningInstructions());
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
