package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.*;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightWaitTimedOutException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.SubflightResult;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
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
 * Wait for the clone controlled database flight to complete and add the result to the appropriate
 * map
 */
public class AwaitCloneControlledAzureDatabaseResourceFlightStep implements Step {

  private final ControlledAzureDatabaseResource resource;
  private final String subflightId;

  public AwaitCloneControlledAzureDatabaseResourceFlightStep(
      ControlledAzureDatabaseResource resource, String subflightId) {
    this.resource = resource;
    this.subflightId = subflightId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {
      SubflightResult subflightResult =
          FlightUtils.waitForSubflightCompletion(context.getStairway(), subflightId);

      // generate cloneDetails
      WsmResourceCloneDetails cloneDetails = new WsmResourceCloneDetails();
      WsmCloneResourceResult cloneResult =
          WorkspaceCloneUtils.flightStatusToCloneResult(
              subflightResult.getFlightStatus(), resource);
      cloneDetails.setResult(cloneResult);

      FlightMap resultMap = subflightResult.getFlightMap();
      var clonedDatabase =
          resultMap.get(JobMapKeys.RESPONSE.getKeyName(), ClonedAzureResource.class);
      cloneDetails.setStewardshipType(StewardshipType.CONTROLLED);
      cloneDetails.setResourceType(WsmResourceType.CONTROLLED_AZURE_DATABASE);
      cloneDetails.setCloningInstructions(resource.getCloningInstructions());
      cloneDetails.setSourceResourceId(resource.getResourceId());
      cloneDetails.setDestinationResourceId(
          Optional.ofNullable(clonedDatabase)
              .map(ClonedAzureResource::resource)
              .map(ControlledResource::getResourceId)
              .orElse(null));
      cloneDetails.setErrorMessage(subflightResult.getFlightErrorMessage());

      cloneDetails.setName(resource.getName());
      cloneDetails.setDescription(resource.getDescription());

      // get the existing map or create a new one
      var resourceIdToResult =
          Optional.ofNullable(
                  context
                      .getWorkingMap()
                      .get(
                          WorkspaceFlightMapKeys.ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
                          new TypeReference<Map<UUID, WsmResourceCloneDetails>>() {}))
              .orElseGet(HashMap::new);
      // add to the map
      resourceIdToResult.put(resource.getResourceId(), cloneDetails);
      context
          .getWorkingMap()
          .put(
              WorkspaceFlightMapKeys.ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
              resourceIdToResult);
    } catch (DatabaseOperationException | FlightWaitTimedOutException e) {
      // retry for database issues or expired wait loop
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    validateRequiredEntries(
        context.getWorkingMap(),
        WorkspaceFlightMapKeys.ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT);
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
