package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;

// TODO WOR-591 This step will be responsible for copying files from the
// source container to the destination
public class CopyAzureStorageContainerBlobsStep implements Step {
  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
