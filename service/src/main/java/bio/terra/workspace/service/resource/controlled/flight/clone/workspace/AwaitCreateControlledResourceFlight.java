package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightWaitTimedOutException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import java.time.Duration;

public class AwaitCreateControlledResourceFlight implements Step {
  private final String flightIdKey;

  public AwaitCreateControlledResourceFlight(String flightIdKey) {
    this.flightIdKey = flightIdKey;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntries(context.getWorkingMap(), flightIdKey);
    var jobId = context.getWorkingMap().get(flightIdKey, String.class);
    try {
      FlightState subflightState =
          FlightUtils.waitForFlightExponential(
              context.getStairway(),
              jobId,
              Duration.ofSeconds(15), // Initial interval
              Duration.ofMinutes(3), // Max interval
              Duration.ofMinutes(30)); // Max flight duration
      if (FlightStatus.SUCCESS != subflightState.getFlightStatus()) {
        // no point in retrying the await step
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            subflightState
                .getException()
                .orElseGet(
                    () ->
                        new RuntimeException(
                            String.format(
                                "Subflight had unexpected status %s. No exception for subflight found.",
                                subflightState.getFlightStatus()))));
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw ie;
    } catch (DatabaseOperationException | FlightWaitTimedOutException e) {
      // Retry for database issues or expired wait loop
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (Exception e) {
      // Error for any other exception
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
  }

  // Can't undo an await step.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
