package bio.terra.workspace.common.flightGenerator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * This class is a proxy for a step result method invocation. It is used to capture step index that
 * will produce the result.
 */
public record StepResultInvocationHandler(int stepIndex) implements InvocationHandler {
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    throw new UnsupportedOperationException(
        "Step result values are not available until the flight is run.");
  }
}
