package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.FLIGHT_POLL_CYCLES;
import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightWaitTimedOutException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/** Wait for completion of the CloneAllResources sub-flight. */
public class AwaitCloneAllResourcesFlightStep implements Step {

  public AwaitCloneAllResourcesFlightStep() {}

  private static final int AWAIT_CLONE_RESOURCES_POLL_SECONDS = 720;

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        ControlledResourceKeys.SOURCE_WORKSPACE_ID,
        JobMapKeys.REQUEST.getKeyName());
    validateRequiredEntries(
        context.getWorkingMap(), ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID);

    var cloneAllResourcesFlightId =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.CLONE_ALL_RESOURCES_FLIGHT_ID, String.class);
    var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);

    try {
      //noinspection deprecation
      FlightState subflightState =
          context
              .getStairway()
              .waitForFlight(
                  cloneAllResourcesFlightId,
                  AWAIT_CLONE_RESOURCES_POLL_SECONDS,
                  FLIGHT_POLL_CYCLES);
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
      FlightMap subflightResultMap = FlightUtils.getResultMapRequired(subflightState);
      // Build the response object from the resource ID to details map. The map won't have been
      // instantiated if there are no resources in the workspace, so just use an empty map in that
      // case.
      var resourceIdToDetails =
          Optional.ofNullable(
                  subflightResultMap.get(
                      ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
                      new TypeReference<Map<UUID, WsmResourceCloneDetails>>() {}))
              .orElse(Collections.emptyMap());
      var apiClonedWorkspace = new ApiClonedWorkspace();
      apiClonedWorkspace.setDestinationWorkspaceId(destinationWorkspace.getWorkspaceId());
      var sourceWorkspaceId =
          context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
      apiClonedWorkspace.setSourceWorkspaceId(sourceWorkspaceId);
      List<ApiResourceCloneDetails> resources =
          resourceIdToDetails.values().stream()
              .map(WsmResourceCloneDetails::toApiModel)
              .collect(Collectors.toList());
      apiClonedWorkspace.setResources(resources);
      // Set overall response for workspace clone flights
      FlightUtils.setResponse(context, apiClonedWorkspace, HttpStatus.OK);
    } catch (DatabaseOperationException | FlightWaitTimedOutException e) {
      // Retry for database issues or expired wait loop
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
