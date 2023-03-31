package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class StoreMetadataStep implements Step {
  private final ResourceDao resourceDao;
  private final WsmResourceStateRule resourceStateRule;

  public StoreMetadataStep(ResourceDao resourceDao, WsmResourceStateRule resourceStateRule) {
    this.resourceDao = resourceDao;
    this.resourceStateRule = resourceStateRule;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    ControlledResource resource =
        FlightUtils.getRequired(
            flightContext.getInputParameters(), ResourceKeys.RESOURCE, ControlledResource.class);

    resourceDao.createResourceStart(resource, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap inputMap = flightContext.getInputParameters();
    final ControlledResource resource =
        inputMap.get(ResourceKeys.RESOURCE, ControlledResource.class);

    resourceDao.createResourceFailure(
        resource,
        flightContext.getFlightId(),
        flightContext.getResult().getException().orElse(null),
        resourceStateRule);

    return StepResult.getStepResultSuccess();
  }
}
