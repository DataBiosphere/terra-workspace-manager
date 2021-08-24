package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;

/**
 * Wait for completion of the CloneAllResources sub-flight.
 */
public class AwaitCloneAllResourcesFlightStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final var cloneAllResourcesFlightId = context.getWorkingMap()
        .get(ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID, String.class);
    try {
      //noinspection deprecation
      context.getStairway().waitForFlight(cloneAllResourcesFlightId, 10, 360);
    } catch (DatabaseOperationException | FlightException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  // no side effects to undo
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
