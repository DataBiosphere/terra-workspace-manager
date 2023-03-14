package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

/**
 * Given a list of resources to be cloned, build a flight with one step for each, run it, and wait.
 * Input Parameters: ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID
 */
public class LaunchCloneAllResourcesFlightStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        JobMapKeys.REQUEST.getKeyName());
    validateRequiredEntries(
        context.getWorkingMap(),
        ControlledResourceKeys.RESOURCES_TO_CLONE,
        ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID);
    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var location = context.getInputParameters().get(ControlledResourceKeys.LOCATION, String.class);
    var cloneAllResourcesFlightId =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID, String.class);

    List<ResourceCloneInputs> resourceCloneInputs =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.RESOURCES_TO_CLONE, new TypeReference<>() {});

    var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    Stairway stairway = context.getStairway();

    FlightMap subflightInputParameters = new FlightMap();
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(ControlledResourceKeys.RESOURCES_TO_CLONE, resourceCloneInputs);
    subflightInputParameters.put(
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspace.getWorkspaceId());
    subflightInputParameters.put(ControlledResourceKeys.LOCATION, location);
    // fields normally set by JobBuilder for identifying jobs
    subflightInputParameters.put(
        WorkspaceFlightMapKeys.WORKSPACE_ID, destinationWorkspace.getWorkspaceId().toString());
    subflightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format(
            "Clone all resources into workspace %s", destinationWorkspace.getWorkspaceId()));

    // Build a CloneAllResourcesFlight
    try {
      stairway.submit(
          cloneAllResourcesFlightId, CloneAllResourcesFlight.class, subflightInputParameters);
    } catch (DuplicateFlightIdException e) {
      // exit early if flight is already going
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
