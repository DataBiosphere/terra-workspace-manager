package bio.terra.workspace.common.utils;

import bio.terra.stairway.*;
import javax.annotation.Nullable;

public class StepResultWithFlightInfo {
  @Nullable private FlightState flightState;
  private StepResult stepResult;

  /**
   * FlightState and StepResult are returned together from a step execution.
   *
   * @param flightState may be null if the flight failed with a runtime exception
   * @param stepResult the result of the step
   */
  public StepResultWithFlightInfo(FlightState flightState, StepResult stepResult) {
    this.flightState = flightState;
    this.stepResult = stepResult;
  }

  public StepResult getStepResult() {
    return stepResult;
  }

  /** Returns true if the step was successful. */
  public boolean isSuccess() {
    return stepResult.isSuccess();
  }

  /** Returns the last status of the flight that was executed during the step. */
  public FlightStatus getFlightStatus() {
    if (flightState == null) {
      // A runtime error occurred in the flight.
      return FlightStatus.FATAL;
    }
    return flightState.getFlightStatus();
  }

  public String getFlightErrorMessage() {
    if (flightState == null) {
      return String.format(
          "Subflight step result had unexpected status %s. No exception for subflight found.",
          stepResult.getStepStatus());
    }
    return FlightUtils.getFlightErrorMessage(flightState);
  }

  /** Will be null if the flight failed in such a way that no map was saved. */
  @Nullable
  public FlightMap getFlightMap() {
    if (flightState == null) {
      return null;
    }
    return FlightUtils.getResultMapRequired(flightState);
  }
}
