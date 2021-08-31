package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;

public class AwaitCreateGcpContextFlightStep implements Step {

  public AwaitCreateGcpContextFlightStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntries(
        context.getWorkingMap(), ControlledResourceKeys.CREATE_CLOUD_CONTEXT_JOB_ID);
    final var jobId =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.CREATE_CLOUD_CONTEXT_JOB_ID, String.class);
    try {
      final FlightState subflightState = context.getStairway().waitForFlight(jobId, 10, 360);
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
    } catch (DatabaseOperationException | FlightException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  // Can't undo an await step.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
