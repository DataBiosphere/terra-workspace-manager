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
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AwaitCloneGcsBucketResourceFlightStep implements Step {

  private final ControlledGcsBucketResource resource;
  private final String subflightId;

  public AwaitCloneGcsBucketResourceFlightStep(
      ControlledGcsBucketResource resource, String subflightId) {
    this.resource = resource;
    this.subflightId = subflightId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // wait for the flight
    try {
      final FlightState subflightState = context.getStairway().waitForFlight(subflightId, 10, 360);
      final FlightStatus subflightStatus = subflightState.getFlightStatus();
      final WsmResourceCloneDetails cloneDetails = new WsmResourceCloneDetails();
      switch (subflightStatus) {
        default:
        case WAITING:
        case READY:
        case QUEUED:
        case READY_TO_RESTART:
        case RUNNING:
          throw new IllegalStateException(
              String.format(
                  "Unexpected status %s for finished flight id %s", subflightStatus, subflightId));
        case SUCCESS:
          if (CloningInstructions.COPY_NOTHING == resource.getCloningInstructions()) {
            cloneDetails.setResult(WsmCloneResourceResult.SKIPPED);
          } else {
            cloneDetails.setResult(WsmCloneResourceResult.SUCCEEDED);
          }
          break;
        case ERROR:
        case FATAL:
          cloneDetails.setResult(WsmCloneResourceResult.FAILED);
          break;
      }
      final FlightMap resultMap =
          subflightState
              .getResultMap()
              .orElseThrow(
                  () ->
                      new MissingRequiredFieldsException(
                          String.format("Result Map not found for flight ID %s", subflightId)));
      final var clonedBucket =
          resultMap.get(JobMapKeys.RESPONSE.getKeyName(), ApiClonedControlledGcpGcsBucket.class);
      cloneDetails.setStewardshipType(StewardshipType.CONTROLLED);
      cloneDetails.setResourceType(WsmResourceType.GCS_BUCKET);
      cloneDetails.setCloningInstructions(resource.getCloningInstructions());
      cloneDetails.setSourceResourceId(resource.getResourceId());
      cloneDetails.setDestinationResourceId(clonedBucket.getBucket().getResourceId());

      // add to the map
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
    return StepResult.getStepResultSuccess();
  }

  // Workspace will be deleted and take controlled resources with it. Nnothing to do here.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
