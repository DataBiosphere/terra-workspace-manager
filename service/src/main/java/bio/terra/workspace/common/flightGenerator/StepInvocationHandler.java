package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Optional;

/**
 * This class is a proxy for a step method invocation. It is used to capture the method and
 * arguments of a step invocation and to add the step to the flight.
 */
public class StepInvocationHandler implements InvocationHandler, Step {
  private final int stepId;
  private final Object target;
  private final FlightGenerator flightGenerator;
  private Method method = null;
  private Optional<Method> undoMethod = Optional.empty();
  private Object[] args;

  public StepInvocationHandler(FlightGenerator flightGenerator, Object target, int stepId) {
    this.stepId = stepId;
    this.target = target;
    this.flightGenerator = flightGenerator;
  }

  public static String outputKey(int inputStepId) {
    return "%d.output".formatted(inputStepId);
  }

  /**
   * This method is called when a method is invoked on the step proxy during instantiation of the
   * flight. It records the method and arguments, adds the step to the flight, and returns a proxy
   * for the return value.
   *
   * @return a proxy for the return value of the method or null if the method returns void
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (this.method != null) {
      throw new IllegalStateException("A step may only be invoked once.");
    }

    // pass through all calls to Object methods and don't intercept them
    if (Arrays.stream(Object.class.getMethods()).toList().contains(method)) {
      return method.invoke(this, args);
    }

    this.flightGenerator.addStep(this);
    this.method = method;
    this.args = args;
    this.undoMethod = findUndoMethod();
    if (method.getReturnType().equals(Void.TYPE)) {
      return null;
    } else {
      return Proxy.newProxyInstance(
          getClass().getClassLoader(),
          new Class[] {method.getReturnType()},
          new StepResultInvocationHandler(stepId));
    }
  }

  /** Do the step method invocation. If the method returns a value, store it in the working map. */
  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {
      var inputs = getInputs(context);
      var results = this.method.invoke(this.target, inputs);
      storeOutputs(context, results);
    } catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof Exception) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL, (Exception) e.getTargetException());
      } else {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
    } catch (Exception e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Get the inputs for the step method invocation. If an input is a step result, get the value from
   * the working map. Otherwise, use the input as is.
   */
  private Object[] getInputs(FlightContext context) {
    if (args == null) {
      return null;
    }

    Object[] inputs = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      if (Proxy.isProxyClass(args[i].getClass())
          && Proxy.getInvocationHandler(args[i]) instanceof StepResultInvocationHandler) {
        var inputStepIndex =
            ((StepResultInvocationHandler) Proxy.getInvocationHandler(args[i])).stepIndex();
        inputs[i] =
            FlightUtils.getRequired(
                context.getWorkingMap(),
                outputKey(inputStepIndex),
                method.getParameters()[i].getType());
      } else {
        inputs[i] = args[i];
      }
    }
    return inputs;
  }

  private void storeOutputs(FlightContext context, Object results) {
    context.getWorkingMap().put(outputKey(stepId), results);
  }

  /** Undo the step method invocation. If an undo method is found, invoke it. */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return undoMethod
        .map(
            undo -> {
              try {
                var inputs = getInputs(context);
                undo.invoke(this.target, inputs);
              } catch (IllegalAccessException | InvocationTargetException e) {
                return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
              }
              return StepResult.getStepResultSuccess();
            })
        .orElseGet(StepResult::getStepResultSuccess);
  }

  private Optional<Method> findUndoMethod() {
    var undoMethodAnnotation = method.getAnnotation(UndoMethod.class);
    if (undoMethodAnnotation != null) {
      try {
        return Optional.of(
            target.getClass().getMethod(undoMethodAnnotation.value(), method.getParameterTypes()));
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("Undo method not found", e);
      }
    } else {
      if (method.getAnnotation(NoUndo.class) != null) {
        return Optional.empty();
      } else {
        throw new IllegalStateException(
            "Undo method not specified, either use @UndoMethod or @NoUndo annotation on method "
                + method.toGenericString());
      }
    }
  }
}
