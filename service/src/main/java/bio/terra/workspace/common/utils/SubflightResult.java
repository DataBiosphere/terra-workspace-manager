package bio.terra.workspace.common.utils;

import bio.terra.stairway.*;
import java.util.Optional;
import javax.annotation.Nullable;

public class SubflightResult {
  @Nullable private FlightState flightState;
  @Nullable private Exception caughtException;

  /** Create a result object based on the `FlightState` of an executed subflight. */
  public SubflightResult(FlightState flightState) {
    this.flightState = flightState;
  }

  /**
   * Create a result object based on an exception that was caught while executing the subflight.
   * Note that this exception will not be an `InterruptedException`, as those are rethrown after
   * interrupting the thread.
   */
  public SubflightResult(Exception exception) {
    this.caughtException = exception;
  }

  /**
   * Convert the state of the executed subflight to a `StepResult` that can be returned from the
   * parent flight's `doStep` method.
   */
  public StepResult convertToStepResult() {
    if (isSuccess()) {
      return StepResult.getStepResultSuccess();
    }
    Exception defaultException =
        Optional.ofNullable(caughtException)
            .orElse(new RuntimeException("Flight failed with an empty exception"));
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        flightState != null
            ? flightState.getException().orElse(defaultException)
            : defaultException);
  }

  /** Returns true if executing the subflight succeeded */
  public boolean isSuccess() {
    return flightState != null && flightState.getFlightStatus() == FlightStatus.SUCCESS;
  }

  /** Returns the `FlightStatus` from executing the subflight. */
  public FlightStatus getFlightStatus() {
    if (flightState == null) {
      // A runtime error occurred in the flight.
      return FlightStatus.FATAL;
    }
    return flightState.getFlightStatus();
  }

  /**
   * If an error was captured while executing the subflight, return its message. Otherwise, return
   * null.
   */
  public String getFlightErrorMessage() {
    if (flightState == null) {
      String message = caughtException != null ? caughtException.getMessage() : null;
      return message == null ? "Flight failed with an empty exception" : message;
    }
    return FlightUtils.getFlightErrorMessage(flightState);
  }

  /**
   * Return the `FlightMap` from the executed subflight. If the subflight failed, the map may be
   * empty.
   */
  public FlightMap getFlightMap() {
    return flightState == null ? new FlightMap() : FlightUtils.getResultMapRequired(flightState);
  }
}
