package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import java.lang.reflect.Proxy;

public class FlightGenerator extends Flight {

  /**
   * The next step id to be assigned to a step. Each step is assigned a unique-within-the-flight id.
   */
  private int nextStepId = 0;

  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param applicationContext Anonymous context meaningful to the application using Stairway
   */
  public FlightGenerator(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
  }

  /**
   * Given an object that implements an interface, create a Step proxy for the object that will be
   * added to the flight.
   */
  protected <T> T createStep(T step) {
    var stepInvocationHandler = new StepInvocationHandler(this, step, nextStepId++);
    var stepProxy =
        Proxy.newProxyInstance(
            step.getClass().getClassLoader(),
            step.getClass().getInterfaces(),
            stepInvocationHandler);

    return (T) stepProxy;
  }

  /**
   * Set the response for the flight.
   *
   * @param response
   * @param <T>
   */
  protected <T> void setResponse(T response) {
    if (Proxy.isProxyClass(response.getClass())
        && Proxy.getInvocationHandler(response) instanceof StepResultInvocationHandler) {
      var responseStepIndex =
          ((StepResultInvocationHandler) Proxy.getInvocationHandler(response)).stepIndex();
      addStep(new SetResponseStep(responseStepIndex));
    } else {
      throw new IllegalArgumentException("Response must be a step");
    }
  }

  /**
   * Add a step to the flight. This is a callback from the StepInvocationHandler when a step
   * function is invoked.
   */
  void addStep(StepInvocationHandler stepInvocationHandler, RetryRule retryRule) {
    super.addStep(stepInvocationHandler, retryRule);
  }
}
