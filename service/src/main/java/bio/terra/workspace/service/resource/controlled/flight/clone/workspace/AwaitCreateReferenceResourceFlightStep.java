package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntriesNonNull;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AwaitCreateReferenceResourceFlightStep implements Step {

  private final ReferencedResource resource;
  private final String flightId;
  private final ResourceDao resourceDao;

  public AwaitCreateReferenceResourceFlightStep(ReferencedResource resource, String flightId, ResourceDao resourceDao) {
    this.resource = resource;
    this.flightId = flightId;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {
      FlightUtils.validateRequiredEntriesNonNull(
          context.getWorkingMap(),
          ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE
      );

      final FlightState subflightState = context.getStairway().waitForFlight(flightId, 10, 10);
      final WsmCloneResourceResult cloneResult =
          WorkspaceCloneUtils.flightStatusToCloneResult(subflightState.getFlightStatus(), resource);
      final WsmResourceCloneDetails cloneDetails = new WsmResourceCloneDetails();
      cloneDetails.setResult(cloneResult);
      final FlightMap resultMap =
          subflightState
              .getResultMap()
              .orElseThrow(
                  () ->
                      new MissingRequiredFieldsException(
                          String.format("Result Map not found for flight ID %s", context)));
      // Input to the create flight
      final var destinationReferencedResource = context.getWorkingMap()
          .get(ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, ReferencedResource.class);
      final var clonedReferencedResourceId = resultMap.get(JobMapKeys.RESPONSE.getKeyName(), UUID.class);
      final WsmResource clonedResource = resourceDao.getResource(destinationReferencedResource.getWorkspaceId(), clonedReferencedResourceId);
      cloneDetails.setResourceType(clonedResource.getResourceType());
      cloneDetails.setStewardshipType(clonedResource.getStewardshipType());
      cloneDetails.setCloningInstructions(clonedResource.getCloningInstructions());
      cloneDetails.setSourceResourceId(resource.getResourceId());
      cloneDetails.setDestinationResourceId(clonedResource.getResourceId());

      // add to the result map
      final var resourceIdToResult =
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

    } catch (DatabaseOperationException | FlightException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    validateRequiredEntriesNonNull(context.getWorkingMap(), ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
