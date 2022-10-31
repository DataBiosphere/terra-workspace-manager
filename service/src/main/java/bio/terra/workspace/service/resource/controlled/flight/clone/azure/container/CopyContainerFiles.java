package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;

public class CopyContainerFiles implements Step {

  public CopyContainerFiles() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    throw new RuntimeException("Not implemented");
  }
}
