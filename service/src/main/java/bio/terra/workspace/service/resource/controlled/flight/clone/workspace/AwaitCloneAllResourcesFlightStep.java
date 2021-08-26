package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import org.springframework.http.HttpStatus;

/** Wait for completion of the CloneAllResources sub-flight. */
public class AwaitCloneAllResourcesFlightStep implements Step {

  public AwaitCloneAllResourcesFlightStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntriesNonNull(
        context.getWorkingMap(), ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID);

    final var cloneAllResourcesFlightId =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID, String.class);
    try {
      //noinspection deprecation
      final FlightState subflightState =
          context.getStairway().waitForFlight(cloneAllResourcesFlightId, 10, 360);
      if (FlightStatus.SUCCESS != subflightState.getFlightStatus()) {
        // no point in retrying the await step
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, subflightState.getException().orElseGet(
            () -> new RuntimeException(String.format("Subflight had unexpected status %s. No exception for subflight found.", subflightState.getFlightStatus()))));
      }
      final FlightMap subflightResultMap =
          subflightState
              .getResultMap()
              .orElseThrow(
                  () ->
                      new MissingRequiredFieldsException(
                          String.format(
                              "ResultMap is missing for flight %s", cloneAllResourcesFlightId)));
      // copy the output from the result map to the current flight's response
      final var subflightResponse =
          subflightResultMap.get(JobMapKeys.RESPONSE.getKeyName(), ApiClonedWorkspace.class);
      // subflight response code for succeeded flight is always HttpStatus.OK
      FlightUtils.setResponse(context, subflightResponse, HttpStatus.OK);
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
