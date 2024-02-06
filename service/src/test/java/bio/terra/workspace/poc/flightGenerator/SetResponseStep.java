package bio.terra.workspace.poc.flightGenerator;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.job.JobMapKeys;

public class SetResponseStep implements Step {
  private final int responseStepIndex;

  public SetResponseStep(int responseStepIndex) {
    this.responseStepIndex = responseStepIndex;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var response = context.getWorkingMap().getRaw(StepInvocationHandler.outputKey(responseStepIndex));
    context.getWorkingMap().putRaw(JobMapKeys.RESPONSE.getKeyName(), response);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
