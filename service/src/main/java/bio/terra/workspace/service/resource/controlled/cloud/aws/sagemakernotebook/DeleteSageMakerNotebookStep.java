package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;

// TODO(TERRA-382): Implement and add delete flight steps. todo-dex

/** A step for deleting a controlled SageMaker notebook instance. */
public class DeleteSageMakerNotebookStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    return null;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return null;
  }
}
