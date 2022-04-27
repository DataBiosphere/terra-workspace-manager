package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class StoreMetadataStep implements Step {
  private final ResourceDao resourceDao;

  public StoreMetadataStep(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputMap = flightContext.getInputParameters();
    final ControlledResource resource =
        inputMap.get(ResourceKeys.RESOURCE, ControlledResource.class);

    resourceDao.createControlledResource(resource);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap inputMap = flightContext.getInputParameters();
    final ControlledResource resource =
        inputMap.get(ResourceKeys.RESOURCE, ControlledResource.class);

    resourceDao.deleteResource(resource.getWorkspaceUuid(), resource.getResourceId());
    return StepResult.getStepResultSuccess();
  }
}
