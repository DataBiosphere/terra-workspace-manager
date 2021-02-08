package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;

public class StoreGoogleBucketMetadataStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    return null;
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return null;
  }
}
