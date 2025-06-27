package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.job.JobMapKeys;
import java.util.Optional;
import java.util.function.Function;

public class SetTypedResponseStep<T, R> implements Step {
  private final int responseStepIndex;
  private final Function<T, R> getter;
  private final Class<T> clazz;

  public SetTypedResponseStep(int responseStepIndex, Function<T, R> getter, Class<T> clazz) {
    this.responseStepIndex = responseStepIndex;
    this.getter = getter;
    this.clazz = clazz;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var input =
        context.getWorkingMap().get(StepInvocationHandler.outputKey(responseStepIndex), clazz);
    var response = Optional.ofNullable(input).map(getter);
    response.ifPresent(r -> context.getWorkingMap().put(JobMapKeys.RESPONSE.getKeyName(), r));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
