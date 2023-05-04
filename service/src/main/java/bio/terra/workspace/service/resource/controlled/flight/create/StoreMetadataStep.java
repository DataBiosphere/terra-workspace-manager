package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;

public class StoreMetadataStep implements Step {
  private final ResourceDao resourceDao;
  private final WsmResourceStateRule resourceStateRule;
  private final WsmResource resource;

  public StoreMetadataStep(
      ResourceDao resourceDao, WsmResourceStateRule resourceStateRule, WsmResource resource) {
    this.resourceDao = resourceDao;
    this.resourceStateRule = resourceStateRule;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    resourceDao.createResourceStart(resource, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {

    resourceDao.createResourceFailure(
        resource,
        flightContext.getFlightId(),
        flightContext.getResult().getException().orElse(null),
        resourceStateRule);

    return StepResult.getStepResultSuccess();
  }
}
