package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntriesNonNull;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DuplicateFlightIdSubmittedException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

/**
 * Given a list of resources to be cloned, build a flight with one step for each, run it, and wait.
 * Input Parameters: ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID
 */
public class LaunchCloneAllResourcesFlightStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntriesNonNull(
        context.getInputParameters(), ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID);
    validateRequiredEntriesNonNull(
        context.getWorkingMap(), ControlledResourceKeys.RESOURCES_TO_CLONE);

    final var cloneAllResourcesFlightId =
        context
            .getInputParameters()
            .get(ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID, String.class);

    final List<ResourceWithFlightId> resourcesAndFlightIds =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.RESOURCES_TO_CLONE, new TypeReference<>() {});
    final Stairway stairway = context.getStairway();
    // exit early if flight is already going
    final FlightMap subflightInputParameters = new FlightMap();
    subflightInputParameters.put(ControlledResourceKeys.RESOURCES_TO_CLONE, resourcesAndFlightIds);
    // Build a CloneAllResourcesFlight
    try {
      stairway.submit(
          cloneAllResourcesFlightId, CloneAllResourcesFlight.class, subflightInputParameters);
    } catch (DuplicateFlightIdSubmittedException e) {
      return StepResult.getStepResultSuccess();
    } catch (StairwayException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
