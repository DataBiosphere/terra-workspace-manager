package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;

public class CloneSingleGcsBucketStep implements Step {

  public CloneSingleGcsBucketStep(ControlledGcsBucketResource resource, String subflightId) {
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    return null;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return null;
  }
}
