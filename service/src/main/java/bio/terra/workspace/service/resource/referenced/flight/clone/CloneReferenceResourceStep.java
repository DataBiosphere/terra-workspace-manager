package bio.terra.workspace.service.resource.referenced.flight.clone;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import org.springframework.http.HttpStatus;

public class CloneReferenceResourceStep implements Step {
  private final ResourceDao resourceDao;
  private final ReferencedResource destinationResource;

  public CloneReferenceResourceStep(
      ResourceDao resourceDao, ReferencedResource destinationResource) {
    this.resourceDao = resourceDao;
    this.destinationResource = destinationResource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var createdResource =
        resourceDao.createResourceSuccess(destinationResource, context.getFlightId());
    FlightUtils.setResponse(context, createdResource, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
