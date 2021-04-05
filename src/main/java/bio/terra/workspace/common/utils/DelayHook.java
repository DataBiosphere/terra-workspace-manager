package bio.terra.workspace.common.utils;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hook to delay a particular step of a given flight by a given duration. This can be useful for
 * testing and debugging, for example to allow manually introducing faults or killing pods during a
 * given flight step.
 */
public class DelayHook implements StairwayHook {
  private static final Logger logger = LoggerFactory.getLogger(DelayHook.class);

  private final int stepIndex;
  private final String flightClassName;
  private final Duration delay;

  /**
   * @param flightClassName - fully qualified class name of a flight
   * @param stepIndex - index of step, starting with zero, to delay
   * @param delay - length of delay
   */
  public DelayHook(String flightClassName, int stepIndex, Duration delay) {
    this.stepIndex = stepIndex;
    this.flightClassName = flightClassName;
    this.delay = delay;
  }

  @Override
  public HookAction startStep(FlightContext context) throws InterruptedException {
    if (context.getFlightClassName().equals(flightClassName)
        && context.getStepIndex() == stepIndex) {
      logger.info(
          "Delaying flight ID {} class {} step {} by {}",
          context.getFlightId(),
          flightClassName,
          stepIndex,
          delay.toString());
      // Manual test typically kills pod now
      TimeUnit.MILLISECONDS.sleep(delay.toMillis());
      logger.info("Resuming flight ID {}", context.getFlightId());
    }
    return HookAction.CONTINUE;
  }
}
