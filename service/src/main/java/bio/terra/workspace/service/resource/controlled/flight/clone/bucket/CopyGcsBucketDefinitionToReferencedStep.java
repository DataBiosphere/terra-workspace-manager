package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import com.google.common.base.Preconditions;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Copy the definition of a GCS bucket into a referenced destination bucket.
 *
 * <p>Preconditions: Source controlled bucket exists in GCS. Cloning instructions are
 * COPY_REFERENCE. DESTINATION_WORKSPACE_ID has been created and is in the input parameters map.
 *
 * <p>Post conditions: A referenced GCS bucket resource is created for the destination. Its
 * RESOURCE_NAME is taken either from the input parameters or the source resource name, if not in
 * the input map. Likewise, its description is taken from the input parameters or the source bucket
 * resource. Bucket namd and creation parameters (lifecycle, storage class, etc) are copied from the
 * source bucket. CLONED_RESOURCE_DEFINITION is put into the working map for undo(). 'The response
 * is set on the flight.
 *
 * <p>Keep in sync with CopyGcsBucketDefinitionToControlledStep.
 */
public class CopyGcsBucketDefinitionToReferencedStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final ControlledGcsBucketResource sourceBucket;
  private final ReferencedResourceService referencedResourceService;
  private final CloningInstructions resolvedCloningInstructions;

  public CopyGcsBucketDefinitionToReferencedStep(
      AuthenticatedUserRequest userRequest,
      ControlledGcsBucketResource sourceBucket,
      ReferencedResourceService referencedResourceService,
      CloningInstructions resolvedCloningInstructions) {
    this.userRequest = userRequest;
    this.sourceBucket = sourceBucket;
    this.referencedResourceService = referencedResourceService;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final FlightMap workingMap = flightContext.getWorkingMap();
    FlightUtils.validateRequiredEntries(
        inputParameters,
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        ControlledResourceKeys.DESTINATION_RESOURCE_ID);
    Preconditions.checkState(
        resolvedCloningInstructions == CloningInstructions.COPY_REFERENCE,
        "CloningInstructions must be COPY_REFERENCE");
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
    final UUID destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final var destinationResourceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);

    ReferencedGcsBucketResource destinationBucketResource =
        WorkspaceCloneUtils.buildDestinationReferencedGcsBucketFromControlled(
            sourceBucket,
            destinationWorkspaceId,
            destinationResourceId,
            resourceName,
            description,
            sourceBucket.getBucketName());

    // Launch a CreateReferenceResourceFlight to make the destination bucket
    final ReferencedGcsBucketResource createdBucket =
        referencedResourceService
            .createReferenceResource(destinationBucketResource, userRequest)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, createdBucket);

    final ApiCreatedControlledGcpGcsBucket apiCreatedBucket =
        new ApiCreatedControlledGcpGcsBucket()
            .gcpBucket(createdBucket.toApiResource())
            .resourceId(createdBucket.getResourceId());
    final ApiClonedControlledGcpGcsBucket apiBucketResult =
        new ApiClonedControlledGcpGcsBucket()
            .effectiveCloningInstructions(resolvedCloningInstructions.toApiModel())
            .bucket(apiCreatedBucket)
            .sourceWorkspaceId(sourceBucket.getWorkspaceId())
            .sourceResourceId(sourceBucket.getResourceId());
    FlightUtils.setResponse(flightContext, apiBucketResult, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  // Delete the row in the resource table
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final ReferencedGcsBucketResource clonedBucket =
        flightContext
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ReferencedGcsBucketResource.class);
    if (clonedBucket != null) {
      referencedResourceService.deleteReferenceResourceForResourceType(
          clonedBucket.getWorkspaceId(),
          clonedBucket.getResourceId(),
          WsmResourceType.REFERENCED_GCP_GCS_BUCKET,
          userRequest);
    }
    return StepResult.getStepResultSuccess();
  }
}
