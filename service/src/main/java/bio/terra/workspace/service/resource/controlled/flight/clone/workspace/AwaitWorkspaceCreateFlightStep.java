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

public class AwaitWorkspaceCreateFlightStep implements Step {

  public AwaitWorkspaceCreateFlightStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntries(context.getWorkingMap(),
        ControlledResourceKeys.WORKSPACE_CREATE_JOB_ID);
    final var workspaceCreateJobId =
        context.getWorkingMap().get(ControlledResourceKeys.WORKSPACE_CREATE_JOB_ID, String.class);
    try {
      final FlightState flightState =
          context.getStairway().waitForFlight(workspaceCreateJobId, 10, 360);
      if (FlightStatus.SUCCESS != flightState.getFlightStatus()) {
        // retrying this step won't help if the flight already failed
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL,
            flightState.getException().orElseGet(() -> new RuntimeException("No exception found for subflight.")));
      }
    } catch (DatabaseOperationException | FlightException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  // No side effects to undo.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
