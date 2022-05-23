package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;

public class UpdateAiNotebookStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    return null;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return null;
  }
}
