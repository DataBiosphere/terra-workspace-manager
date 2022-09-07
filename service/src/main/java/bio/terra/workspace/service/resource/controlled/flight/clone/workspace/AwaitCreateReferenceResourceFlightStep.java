package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightWaitTimedOutException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AwaitCreateReferenceResourceFlightStep implements Step {

  private final ReferencedResource sourceResource;
  private final String flightId;
  private final ResourceDao resourceDao;

  public AwaitCreateReferenceResourceFlightStep(
      ReferencedResource resource, String flightId, ResourceDao resourceDao) {
    this.sourceResource = resource;
    this.flightId = flightId;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {

      // add to the result map
      var resourceIdToResult =
          Optional.ofNullable(
                  context
                      .getWorkingMap()
                      .get(
                          ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
                          new TypeReference<Map<UUID, WsmResourceCloneDetails>>() {}))
              .orElseGet(HashMap::new);
      WsmResourceCloneDetails cloneDetails = new WsmResourceCloneDetails();

      if (CloningInstructions.COPY_REFERENCE == sourceResource.getCloningInstructions()) {
        FlightUtils.validateRequiredEntries(
            context.getWorkingMap(), ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE);
        FlightState subflightState =
            FlightUtils.waitForFlightExponential(
                context.getStairway(), flightId, Duration.ofMillis(50), Duration.ofMinutes(5));
        WsmCloneResourceResult cloneResult =
            WorkspaceCloneUtils.flightStatusToCloneResult(
                subflightState.getFlightStatus(), sourceResource);
        cloneDetails.setResult(cloneResult);
        FlightMap resultMap = FlightUtils.getResultMapRequired(subflightState);

        // Input to the create flight
        var destinationReferencedResource =
            context
                .getWorkingMap()
                .get(
                    ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE,
                    ReferencedResource.class);
        var clonedReferencedResourceId =
            resultMap.get(JobMapKeys.RESPONSE.getKeyName(), UUID.class);

        // Use the destination referenced resource for things that are fixed over
        // the operation, and the one from the database to verify the resource ID
        cloneDetails.setResourceType(destinationReferencedResource.getResourceType());
        cloneDetails.setStewardshipType(destinationReferencedResource.getStewardshipType());
        cloneDetails.setCloningInstructions(destinationReferencedResource.getCloningInstructions());
        cloneDetails.setSourceResourceId(sourceResource.getResourceId());
        cloneDetails.setDestinationResourceId(clonedReferencedResourceId);
        cloneDetails.setErrorMessage(FlightUtils.getFlightErrorMessage(subflightState));
        cloneDetails.setName(sourceResource.getName());
        cloneDetails.setDescription(sourceResource.getDescription());
      } else {
        // No flight was created
        cloneDetails.setResult(WsmCloneResourceResult.SKIPPED);
        cloneDetails.setResourceType(sourceResource.getResourceType());
        cloneDetails.setStewardshipType(sourceResource.getStewardshipType());
        cloneDetails.setCloningInstructions(sourceResource.getCloningInstructions());
        cloneDetails.setSourceResourceId(sourceResource.getResourceId());
        cloneDetails.setDestinationResourceId(null);
        cloneDetails.setErrorMessage(null);
        cloneDetails.setName(sourceResource.getName());
        cloneDetails.setDescription(sourceResource.getDescription());
      }
      resourceIdToResult.put(sourceResource.getResourceId(), cloneDetails);
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

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
