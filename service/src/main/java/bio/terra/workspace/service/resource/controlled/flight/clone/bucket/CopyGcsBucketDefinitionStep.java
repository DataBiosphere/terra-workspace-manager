package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils.buildDestinationControlledGcsBucket;

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
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;

/**
 * Copy the definition of a GCS bucket (i.e. everything but the data) into a destionation bucket.
 *
 * <p>Preconditions: Source bucket exists in GCS. Cloning insitructions are either COPY_RESOURCE or
 * COPY_DEFINITION. DESTINATION_WORKSPACE_ID has been created and is in the input parameters map.
 *
 * <p>Post conditions: A controlled GCS bucket resource is created for the destination. Its
 * RESOURCE_NAME is taken either from the input parameters or the source resource name, if not in
 * the input map. Likewise, its description is taken from the input parameters or the source bucket
 * resource. Creation parameters (lifecycle, storage class, etc) are copied from the source bucket.
 * CLONED_RESOURCE_DEFINITION is put into the working map for future steps. A
 * CLONE_DEFINITION_RESULT object is put into the working map, and if the cloning instructions are
 * COPY_DEFINITION, the response is set on the flight.
 */
public class CopyGcsBucketDefinitionStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledGcsBucketResource sourceBucket;
  private final ControlledResourceService controlledResourceService;
  private final CloningInstructions resolvedCloningInstructions;
  private final WorkspaceActivityLogService workspaceActivityLogService;

  public CopyGcsBucketDefinitionStep(
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledGcsBucketResource sourceBucket,
      ControlledResourceService controlledResourceService,
      CloningInstructions resolvedCloningInstructions,
      WorkspaceActivityLogService workspaceActivityLogService) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.sourceBucket = sourceBucket;
    this.controlledResourceService = controlledResourceService;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
    FlightUtils.validateRequiredEntries(
        inputParameters,
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        ControlledResourceKeys.DESTINATION_RESOURCE_ID);
    String resourceName =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ResourceKeys.RESOURCE_NAME,
            ResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    String description =
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            ResourceKeys.RESOURCE_DESCRIPTION,
            ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);

    UUID destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    String bucketName =
        Optional.ofNullable(
                inputParameters.get(ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class))
            .orElse(
                // If the source bucket uses the auto-generated cloud name and the destination
                // bucket attempt to do the same, the name will crash as the bucket name must be
                // globally unique. Thus, we add cloned- as prefix to the resource name to prevent
                // crashing.
                ControlledGcsBucketHandler.getHandler()
                    .generateCloudName(destinationWorkspaceId, "cloned-" + resourceName));
    UUID destinationFolderId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_FOLDER_ID, UUID.class);
    // Store effective bucket name for destination
    workingMap.put(ControlledResourceKeys.DESTINATION_BUCKET_NAME, bucketName);
    UUID destinationResourceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);
    ApiGcpGcsBucketCreationParameters destinationCreationParameters =
        getDestinationCreationParameters(inputParameters, workingMap);
    // bucket resource for create flight
    ControlledGcsBucketResource destinationBucketResource =
        buildDestinationControlledGcsBucket(
            sourceBucket,
            destinationWorkspaceId,
            destinationResourceId,
            destinationFolderId,
            resourceName,
            description,
            bucketName,
            samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
            destinationCreationParameters.getLocation());

    ControlledResourceIamRole iamRole =
        IamRoleUtils.getIamRoleForAccessScope(sourceBucket.getAccessScope());

    // Launch a CreateControlledResourceFlight to make the destination bucket
    ControlledGcsBucketResource clonedBucket =
        controlledResourceService
            .createControlledResourceSync(
                destinationBucketResource, iamRole, userRequest, destinationCreationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedBucket);

    var apiCreatedBucket =
        new ApiCreatedControlledGcpGcsBucket()
            .gcpBucket(clonedBucket.toApiResource())
            .resourceId(destinationBucketResource.getResourceId());

    var apiBucketResult =
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
    var clonedBucket =
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

  @Nullable
  private ApiGcpGcsBucketCreationParameters getDestinationCreationParameters(
      FlightMap inputParameters, FlightMap workingMap) {
    var sourceCreationParameters =
        workingMap.get(
            ControlledResourceKeys.CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
    Optional<String> suppliedLocation =
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
}
