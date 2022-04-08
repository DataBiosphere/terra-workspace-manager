package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.IamRoleUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;

public class CopyGcsBucketDefinitionStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final ControlledGcsBucketResource sourceBucket;
  private final ControlledResourceService controlledResourceService;
  private final CloningInstructions resolvedCloningInstructions;

  public CopyGcsBucketDefinitionStep(
      AuthenticatedUserRequest userRequest,
      ControlledGcsBucketResource sourceBucket,
      ControlledResourceService controlledResourceService,
      CloningInstructions resolvedCloningInstructions) {
    this.userRequest = userRequest;
    this.sourceBucket = sourceBucket;
    this.controlledResourceService = controlledResourceService;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final FlightMap workingMap = flightContext.getWorkingMap();
    FlightUtils.validateRequiredEntries(inputParameters,
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID);
     // todo: handle COPY_REFERENCE PF-811, PF-812
    final String resourceName =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ResourceKeys.RESOURCE_NAME,
            ResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    final String description =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ResourceKeys.RESOURCE_DESCRIPTION,
            ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);
    final String bucketName =
        Optional.ofNullable(
                inputParameters.get(ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class))
            .orElseGet(this::randomBucketName);
    final PrivateResourceState privateResourceState =
        sourceBucket.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE
            ? PrivateResourceState.INITIALIZING
            : PrivateResourceState.NOT_APPLICABLE;
    // Store effective bucket name for destination
    workingMap.put(ControlledResourceKeys.DESTINATION_BUCKET_NAME, bucketName);
    final UUID destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    // bucket resource for create flight
    ControlledGcsBucketResource destinationBucketResource =
        ControlledGcsBucketResource.builder()
            .bucketName(bucketName)
            .common(ControlledResourceFields.builder()
              .workspaceId(destinationWorkspaceId)
              .resourceId(UUID.randomUUID()) // random ID for new resource
              .name(resourceName)
              .description(description)
              .cloningInstructions(sourceBucket.getCloningInstructions())
              .assignedUser(sourceBucket.getAssignedUser().orElse(null))
              .accessScope(sourceBucket.getAccessScope())
              .managedBy(sourceBucket.getManagedBy())
              .applicationId(sourceBucket.getApplicationId())
              .privateResourceState(privateResourceState)
              .build())
            .build();

    final ApiGcpGcsBucketCreationParameters destinationCreationParameters =
        getDestinationCreationParameters(inputParameters);

    final ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceBucket.getAccessScope());

    // Launch a CreateControlledResourcesFlight to make the destination bucket
    final ControlledGcsBucketResource clonedBucket =
        controlledResourceService
            .createControlledResourceSync(
                destinationBucketResource, iamRole, userRequest, destinationCreationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedBucket);

    final ApiCreatedControlledGcpGcsBucket apiCreatedBucket =
        new ApiCreatedControlledGcpGcsBucket()
            .gcpBucket(clonedBucket.toApiResource())
            .resourceId(destinationBucketResource.getResourceId());

    final ApiClonedControlledGcpGcsBucket apiBucketResult =
        new ApiClonedControlledGcpGcsBucket()
            .effectiveCloningInstructions(resolvedCloningInstructions.toApiModel())
            .bucket(apiCreatedBucket)
            .sourceWorkspaceId(sourceBucket.getWorkspaceId())
            .sourceResourceId(sourceBucket.getResourceId());
    workingMap.put(ControlledResourceKeys.CLONE_DEFINITION_RESULT, apiBucketResult);
    if (resolvedCloningInstructions.equals(CloningInstructions.COPY_DEFINITION)) {
      FlightUtils.setResponse(flightContext, apiBucketResult, HttpStatus.OK);
    }
    return StepResult.getStepResultSuccess();
  }

  // Delete the bucket and its row in the resource table
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final ControlledGcsBucketResource clonedBucket =
        flightContext
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledGcsBucketResource.class);
    if (clonedBucket != null) {
      controlledResourceService.deleteControlledResourceSync(
          clonedBucket.getWorkspaceId(), clonedBucket.getResourceId(), userRequest);
    }
    return StepResult.getStepResultSuccess();
  }

  @Nullable private ApiGcpGcsBucketCreationParameters getDestinationCreationParameters(
      FlightMap inputParameters) {
    @Nullable final ApiGcpGcsBucketCreationParameters sourceCreationParameters =
        inputParameters.get(
            ControlledResourceKeys.CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
    final Optional<String> suppliedLocation =
        Optional.ofNullable(inputParameters.get(ControlledResourceKeys.LOCATION, String.class));

    // Override the location parameter if it was specified
    if (suppliedLocation.isPresent()) {
      return new ApiGcpGcsBucketCreationParameters()
          .defaultStorageClass(sourceCreationParameters.getDefaultStorageClass())
          .lifecycle(sourceCreationParameters.getLifecycle())
          .name(sourceCreationParameters.getName())
          .location(suppliedLocation.get());
    } else {
      return sourceCreationParameters;
    }
  }

  private String randomBucketName() {
    return "terra-wsm-" + UUID.randomUUID().toString().toLowerCase();
  }
}
