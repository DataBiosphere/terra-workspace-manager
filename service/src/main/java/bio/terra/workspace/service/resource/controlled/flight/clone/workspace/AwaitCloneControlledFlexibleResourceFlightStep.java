package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightWaitTimedOutException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.StepResultWithFlightInfo;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Wait for the clone flexible resource flight to complete and add the result to the appropriate
 * map.
 */
public class AwaitCloneControlledFlexibleResourceFlightStep implements Step {

  private final ControlledFlexibleResource resource;
  private final String subFlightId;

  public AwaitCloneControlledFlexibleResourceFlightStep(
      ControlledFlexibleResource resource, String subFlightId) {
    this.resource = resource;
    this.subFlightId = subFlightId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    try {
      StepResultWithFlightInfo subflightResult =
          FlightUtils.waitForSubflightCompletionAndReturnFlightInfo(
              flightContext.getStairway(), subFlightId);

      // Get the existing map, or create a new one.
      var resourceIdToResult =
          Optional.ofNullable(
                  flightContext
                      .getWorkingMap()
                      .get(
                          WorkspaceFlightMapKeys.ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
                          new TypeReference<Map<UUID, WsmResourceCloneDetails>>() {}))
              .orElseGet(HashMap::new);

      // Generate the cloneDetails.
      WsmResourceCloneDetails cloneDetails = new WsmResourceCloneDetails();
      WsmCloneResourceResult cloneResult =
          WorkspaceCloneUtils.flightStatusToCloneResult(
              subflightResult.getFlightStatus(), resource);
      cloneDetails.setResult(cloneResult);

      FlightMap resultMap = subflightResult.getFlightMap();
      ControlledFlexibleResource clonedFlexResource =
          resultMap != null
              ? resultMap.get(JobMapKeys.RESPONSE.getKeyName(), ControlledFlexibleResource.class)
              : null;
      cloneDetails.setStewardshipType(StewardshipType.CONTROLLED);
      cloneDetails.setResourceType(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE);
      cloneDetails.setCloningInstructions(resource.getCloningInstructions());
      cloneDetails.setSourceResourceId(resource.getResourceId());
      cloneDetails.setDestinationResourceId(
          Optional.ofNullable(clonedFlexResource)
              .map(ControlledFlexibleResource::getResourceId)
              .orElse(null));
      cloneDetails.setErrorMessage(subflightResult.getFlightErrorMessage());

      cloneDetails.setName(resource.getName());
      cloneDetails.setDescription(resource.getDescription());

      // Place in the map.
      resourceIdToResult.put(resource.getResourceId(), cloneDetails);
      flightContext
          .getWorkingMap()
          .put(
              WorkspaceFlightMapKeys.ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
              resourceIdToResult);
    } catch (DatabaseOperationException | FlightWaitTimedOutException e) {
      // Retry for database issues or expired wait loop
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    validateRequiredEntries(
        flightContext.getWorkingMap(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT);
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
