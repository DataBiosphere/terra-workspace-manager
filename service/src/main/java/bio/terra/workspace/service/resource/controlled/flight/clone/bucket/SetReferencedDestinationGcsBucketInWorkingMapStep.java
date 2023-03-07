package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import com.google.common.base.Preconditions;
import java.util.UUID;

/**
 * Adds ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE and ResourceKeys.RESOURCE_TYPE to
 * working map, for CreateReferenceMetadataStep to use.
 */
public class SetReferencedDestinationGcsBucketInWorkingMapStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledGcsBucketResource sourceBucket;
  private final ReferencedResourceService referencedResourceService;
  private final CloningInstructions resolvedCloningInstructions;

  public SetReferencedDestinationGcsBucketInWorkingMapStep(
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledGcsBucketResource sourceBucket,
      ReferencedResourceService referencedResourceService,
      CloningInstructions resolvedCloningInstructions) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.sourceBucket = sourceBucket;
    this.referencedResourceService = referencedResourceService;
    this.resolvedCloningInstructions = resolvedCloningInstructions;
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
    Preconditions.checkState(
        resolvedCloningInstructions == CloningInstructions.COPY_REFERENCE
            || resolvedCloningInstructions == CloningInstructions.LINK_REFERENCE,
        "CloningInstructions must be COPY_REFERENCE or LINK_REFERENCE");
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
    UUID destinationResourceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);
    UUID destinationFolderId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_FOLDER_ID, UUID.class);

    ReferencedGcsBucketResource destinationBucketResource =
        sourceBucket
            .buildReferencedClone(
                destinationWorkspaceId,
                destinationResourceId,
                destinationFolderId,
                resourceName,
                description,
                samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest))
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);

    workingMap.put(
        ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, destinationBucketResource);
    workingMap.put(ResourceKeys.RESOURCE_TYPE, destinationBucketResource.getResourceType());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
