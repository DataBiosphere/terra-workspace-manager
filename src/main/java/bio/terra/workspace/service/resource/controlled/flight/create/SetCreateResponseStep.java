package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;

public class SetCreateResponseStep implements Step {
  private final ControlledResource resource;

  public SetCreateResponseStep(ControlledResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    // Return the input resource as the response; it is filled in properly on the request,
    // so has all the right data. And if we have made it this far, we have made the request
    // contents true.
    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(JobMapKeys.RESPONSE.getKeyName(), resource);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
