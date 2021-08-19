package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;

public class CloneEachResourceStep implements Step {

  public static final Duration FLIGHT_POLL_INTERVAL = Duration.ofSeconds(10);
  public static final int FLIGHT_POLL_CYCLES = 100;
  private final ReferencedResourceService referencedResourceService;
  private final ControlledResourceService controlledResourceService;

  public CloneEachResourceStep(
      ReferencedResourceService referencedResourceService,
      ControlledResourceService controlledResourceService) {
    this.referencedResourceService = referencedResourceService;
    this.controlledResourceService = controlledResourceService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final var destinationWorkspaceId =
        context.getWorkingMap().get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final var location =
        context.getInputParameters().get(ControlledResourceKeys.LOCATION, String.class);
    // get the list
    final List<WsmResource> resourcesToClone =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.RESOURCES_TO_CLONE, new TypeReference<>() {});
    // Get or create the map from entry to result. This will be incrementally upgraded.
    final Map<UUID, WsmResourceCloneDetails> resourceIdToResult =
        Optional.ofNullable(
                context
                    .getWorkingMap()
                    .get(
                        ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
                        new TypeReference<Map<UUID, WsmResourceCloneDetails>>() {}))
            .orElseGet(HashMap::new);

    // Clone first resource on the list that isn't in the map.
    final Optional<WsmResource> resourceMaybe =
        resourcesToClone.stream()
            .filter(r -> !resourceIdToResult.containsKey(r.getResourceId()))
            .findFirst();
    if (resourceMaybe.isPresent()) {
      final WsmResource resource = resourceMaybe.get();
      try {
        final WsmResourceCloneDetails cloneDetails =
            clone(resource, destinationWorkspaceId, userRequest, location, context.getStairway());
        resourceIdToResult.put(resource.getResourceId(), cloneDetails);
        context
            .getWorkingMap()
            .put(ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT, resourceIdToResult);
        return new StepResult(StepStatus.STEP_RESULT_RERUN); // do next entry
      } catch (DatabaseOperationException | FlightException e) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
    } else {
      // Done with all resources. Build the response object and return
      final var sourceWorkspaceId =
          context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);

      final ApiClonedWorkspace response =
          new ApiClonedWorkspace()
              .sourceWorkspaceId(sourceWorkspaceId)
              .destinationWorkspaceId(destinationWorkspaceId)
              .resources(
                  resourceIdToResult.values().stream()
                      .map(WsmResourceCloneDetails::toApiModel)
                      .collect(Collectors.toList()));
      FlightUtils.setResponse(context, response, HttpStatus.OK);
      return StepResult.getStepResultSuccess();
    }
  }

  // No need to undo as entire workspace will be deleted by an earlier step's undo.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private WsmResourceCloneDetails clone(
      WsmResource resource,
      UUID destinationWorkspaceId,
      AuthenticatedUserRequest userRequest,
      @Nullable String location,
      Stairway stairway)
      throws InterruptedException, DatabaseOperationException, FlightException {
    switch (resource.getStewardshipType()) {
      case REFERENCED:
        return cloneReferencedResource(
            resource.castToReferencedResource(), destinationWorkspaceId, userRequest);
      case CONTROLLED:
        switch (resource.getResourceType()) {
          case GCS_BUCKET:
            return cloneGcsBucket(
                resource.castToControlledResource().castToGcsBucketResource(),
                destinationWorkspaceId,
                userRequest,
                stairway,
                location);
          case BIG_QUERY_DATASET:
            return cloneBigQueryDataset(
                resource.castToControlledResource().castToBigQueryDatasetResource(),
                destinationWorkspaceId,
                userRequest,
                stairway,
                location);
          case AI_NOTEBOOK_INSTANCE:
          case DATA_REPO_SNAPSHOT:
          default:
            throw new InternalLogicException(
                String.format(
                    "Resource type %s is not supported for clone", resource.getResourceType()));
        }
      default:
        throw new InternalLogicException(
            String.format("Stewardship type %s is not recognized", resource.getStewardshipType()));
    }
  }

  private WsmResourceCloneDetails cloneReferencedResource(
      ReferencedResource resource,
      UUID destinationWorkspaceId,
      AuthenticatedUserRequest userRequest) {
    final String description =
        String.format("Clone of Referenced Resource %s", resource.getResourceId());
    final ReferencedResource clonedResource =
        referencedResourceService.cloneReferencedResource(
            resource, destinationWorkspaceId, null, description, userRequest);
    final WsmResourceCloneDetails result = new WsmResourceCloneDetails();
    result.setResourceType(resource.getResourceType());
    result.setStewardshipType(resource.getStewardshipType());
    result.setDestinationResourceId(clonedResource.getResourceId());
    result.setResult(WsmCloneResourceResult.SUCCEEDED);
    result.setCloningInstructions(
        CloningInstructions
            .COPY_REFERENCE); // FIXME(jaycarlton) reference clone doesn't use cloning instructions
    return result;
  }

  private WsmResourceCloneDetails cloneGcsBucket(
      ControlledGcsBucketResource resource,
      UUID destinationWorkspaceId,
      AuthenticatedUserRequest userRequest,
      Stairway stairway,
      @Nullable String location)
      throws InterruptedException, DatabaseOperationException, FlightException {
    final String description =
        String.format("Clone of GCS Bucket Resource %s", resource.getResourceId());
    final String jobId = UUID.randomUUID().toString();
    final ApiJobControl jobControl = new ApiJobControl().id(jobId);
    controlledResourceService.cloneGcsBucket(
        resource.getWorkspaceId(),
        resource.getResourceId(),
        destinationWorkspaceId,
        jobControl,
        userRequest,
        null,
        description,
        null,
        location,
        null);

    final FlightState flightState =
        stairway.waitForFlight(
            jobId, Math.toIntExact(FLIGHT_POLL_INTERVAL.toSeconds()), FLIGHT_POLL_CYCLES);
    final FlightMap subFlightResultMap =
        flightState
            .getResultMap()
            .orElseThrow(
                () -> new MissingRequiredFieldsException("Sub-flight result map not found."));
    WsmCloneResourceResult resultStatus;
    UUID destinationResourceId = null;
    final var response =
        subFlightResultMap.get(
            JobMapKeys.RESPONSE.getKeyName(), ApiClonedControlledGcpGcsBucket.class);
    if (null == response) {
      resultStatus = WsmCloneResourceResult.FAILED;
    } else if (null == response.getBucket()) {
      if (CloningInstructions.COPY_NOTHING == resource.getCloningInstructions()) {
        // bucket isn't populated when the cloning instructions are COPY_NOTHING
        resultStatus = WsmCloneResourceResult.SKIPPED;
      } else {
        throw new MissingRequiredFieldsException("Bucket was not set on response object.");
      }
    } else {
      resultStatus = WsmCloneResourceResult.SUCCEEDED;
      destinationResourceId = response.getBucket().getResourceId();
    }
    // get the clone result
    final WsmResourceCloneDetails result = new WsmResourceCloneDetails();
    result.setCloningInstructions(resource.getCloningInstructions());
    result.setResult(resultStatus);
    result.setResourceType(resource.getResourceType());
    result.setSourceResourceId(resource.getResourceId());
    result.setDestinationResourceId(destinationResourceId);
    result.setStewardshipType(resource.getStewardshipType());
    return result;
  }

  private WsmResourceCloneDetails cloneBigQueryDataset(
      ControlledBigQueryDatasetResource resource,
      UUID destinationWorkspaceId,
      AuthenticatedUserRequest userRequest,
      Stairway stairway,
      @Nullable String location)
      throws InterruptedException, DatabaseOperationException, FlightException {
    final String description =
        String.format("Clone of BigQuery dataset resource %s", resource.getResourceId());
    final String jobId = UUID.randomUUID().toString();
    final ApiJobControl jobControl = new ApiJobControl().id(jobId);
    controlledResourceService.cloneBigQueryDataset(
        resource.getWorkspaceId(),
        resource.getResourceId(),
        destinationWorkspaceId,
        jobControl,
        userRequest,
        null,
        description,
        null,
        location,
        null);
    final FlightState flightState =
        stairway.waitForFlight(
            jobId, Math.toIntExact(FLIGHT_POLL_INTERVAL.toSeconds()), FLIGHT_POLL_CYCLES);
    final FlightMap subFlightResultMap =
        flightState
            .getResultMap()
            .orElseThrow(
                () -> new MissingRequiredFieldsException("Sub-flight result map not found."));
    WsmCloneResourceResult resultStatus;
    UUID destinationResourceId = null;
    final var response =
        subFlightResultMap.get(
            JobMapKeys.RESPONSE.getKeyName(), ApiClonedControlledGcpBigQueryDataset.class);
    if (null == response) {
      resultStatus = WsmCloneResourceResult.FAILED;
    } else if (null == response.getDataset()) {
      if (CloningInstructions.COPY_NOTHING == resource.getCloningInstructions()) {
        // bucket isn't populated when the cloning instructions are COPY_NOTHING
        resultStatus = WsmCloneResourceResult.SKIPPED;
      } else {
        throw new MissingRequiredFieldsException("Dataset was not set on response object.");
      }
    } else {
      resultStatus = WsmCloneResourceResult.SUCCEEDED;
      destinationResourceId = response.getDataset().getMetadata().getResourceId();
    }
    // get the clone result
    final WsmResourceCloneDetails result = new WsmResourceCloneDetails();
    result.setCloningInstructions(resource.getCloningInstructions());
    result.setResult(resultStatus);
    result.setResourceType(resource.getResourceType());
    result.setSourceResourceId(resource.getResourceId());
    result.setDestinationResourceId(destinationResourceId);
    result.setStewardshipType(resource.getStewardshipType());
    return result;
  }
}
