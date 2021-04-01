package bio.terra.workspace.common.utils;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hook to delay a particular step of a given flight by a given duration. */
public class DelayHook implements StairwayHook {
  private static final Logger logger = LoggerFactory.getLogger(DelayHook.class);

  private final int stepIndex;
  private final String flightClassName;
  private final Duration delay;

  public <T> DelayHook(int stepIndex, String flightClassName, Duration delay) {
    this.stepIndex = stepIndex;
    this.flightClassName = flightClassName;
    this.delay = delay;
  }

  @Override
  public HookAction startStep(FlightContext context) throws InterruptedException {
    if (context.getFlightClassName().equals(flightClassName)
        && context.getStepIndex() == stepIndex) {
      logger.info("Delaying flight {} step {} by {}", flightClassName, stepIndex, delay.toString());
      TimeUnit.MILLISECONDS.sleep(delay.toMillis());
    }
    return HookAction.CONTINUE;
  }
}
