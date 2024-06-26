package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.CLONE_SUBFLIGHT_FACTOR_INCREASE;
import static bio.terra.workspace.common.utils.FlightUtils.CLONE_SUBFLIGHT_INITIAL_SLEEP;
import static bio.terra.workspace.common.utils.FlightUtils.CLONE_SUBFLIGHT_MAX_SLEEP;
import static bio.terra.workspace.common.utils.FlightUtils.CLONE_SUBFLIGHT_TOTAL_DURATION;
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
import bio.terra.workspace.common.utils.SubflightResult;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Wait for the clone storage container flight to complete and add the result to the appropriate map
 */
public class AwaitCloneControlledAzureStorageContainerResourceFlightStep implements Step {

  private final ControlledAzureStorageContainerResource resource;
  private final String subflightId;

  public AwaitCloneControlledAzureStorageContainerResourceFlightStep(
      ControlledAzureStorageContainerResource resource, String subflightId) {
    this.resource = resource;
    this.subflightId = subflightId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {
      SubflightResult subflightResult =
          FlightUtils.waitForSubflightCompletion(
              context.getStairway(),
              subflightId,
              CLONE_SUBFLIGHT_TOTAL_DURATION,
              CLONE_SUBFLIGHT_INITIAL_SLEEP,
              CLONE_SUBFLIGHT_FACTOR_INCREASE,
              CLONE_SUBFLIGHT_MAX_SLEEP);

      WsmResourceCloneDetails cloneDetails = new WsmResourceCloneDetails();
      WsmCloneResourceResult cloneResult =
          WorkspaceCloneUtils.flightStatusToCloneResult(
              subflightResult.getFlightStatus(), resource);
      cloneDetails.setResult(cloneResult);

      FlightMap resultMap = subflightResult.getFlightMap();
      var clonedContainer =
          resultMap.get(JobMapKeys.RESPONSE.getKeyName(), ClonedAzureResource.class);
      cloneDetails.setStewardshipType(StewardshipType.CONTROLLED);
      cloneDetails.setResourceType(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
      cloneDetails.setCloningInstructions(resource.getCloningInstructions());
      cloneDetails.setSourceResourceId(resource.getResourceId());
      cloneDetails.setDestinationResourceId(
          Optional.ofNullable(clonedContainer)
              .map(ClonedAzureResource::resource)
              .map(ControlledResource::getResourceId)
              .orElse(null));

      cloneDetails.setErrorMessage(subflightResult.getFlightErrorMessage());
      cloneDetails.setName(resource.getName());
      cloneDetails.setDescription(resource.getDescription());
      // add to the map
      var resourceIdToResult =
          Optional.ofNullable(
                  context
                      .getWorkingMap()
                      .get(
                          ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
                          new TypeReference<Map<UUID, WsmResourceCloneDetails>>() {}))
              .orElseGet(HashMap::new);
      resourceIdToResult.put(resource.getResourceId(), cloneDetails);
      context
          .getWorkingMap()
          .put(ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT, resourceIdToResult);

    } catch (DatabaseOperationException | FlightWaitTimedOutException e) {
      // Retry for database issues or expired wait loop
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    validateRequiredEntries(
        context.getWorkingMap(), ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT);
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
