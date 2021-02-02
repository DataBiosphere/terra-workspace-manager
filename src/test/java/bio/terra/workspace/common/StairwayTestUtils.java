package bio.terra.workspace.common;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdSubmittedException;
import bio.terra.stairway.exception.StairwayExecutionException;
import java.time.Duration;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/** Test utilities for working with Stairway. */
public class StairwayTestUtils {
  private StairwayTestUtils() {}

  /**
   * Submits the flight and block until Stairway completes it by polling regularly until the timeout
   * is reached.
   */
  public static @NotNull FlightState blockUntilFlightCompletes(
      @NotNull Stairway stairway,
      Class<? extends Flight> flightClass,
      FlightMap inputParameters,
      @NotNull Duration timeout)
      throws DatabaseOperationException, StairwayExecutionException, InterruptedException,
          DuplicateFlightIdSubmittedException {
    String flightId = stairway.createFlightId();
    stairway.submit(flightId, flightClass, inputParameters);
    return pollUntilComplete(flightId, stairway, timeout.dividedBy(20), timeout);
  }

  /**
   * Polls stairway until the flight for {@code flightId} completes, or this has polled {@code
   * numPolls} times every {@code pollInterval}.
   */
  public static FlightState pollUntilComplete(
      String flightId,
      @NotNull Stairway stairway,
      @NotNull Duration pollInterval,
      @NotNull Duration timeout)
      throws InterruptedException, DatabaseOperationException {
    for (Instant deadline = Instant.now().plus(timeout);
        Instant.now().isBefore(deadline);
        Thread.sleep(pollInterval.toMillis())) {
      FlightState flightState = stairway.getFlightState(flightId);
      if (!flightState.isActive()) {
        return flightState;
      }
    }
    throw new InterruptedException(
        String.format("Flight [%s] did not complete in the allowed wait time.", flightId));
  }

  /**
   * A {@link Step} that always fatally errors on {@link Step#doStep(FlightContext)}. Undo is ok.
   */
  public static class ErrorDoStep implements Step {
    @Override
    public @NotNull StepResult doStep(FlightContext flightContext) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      return StepResult.getStepResultSuccess();
    }
  }
}
