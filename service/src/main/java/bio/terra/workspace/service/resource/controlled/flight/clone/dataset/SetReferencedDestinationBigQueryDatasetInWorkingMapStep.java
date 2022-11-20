package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import com.google.common.base.Preconditions;
import java.util.UUID;

/**
 * Adds ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE and ResourceKeys.RESOURCE_TYPE to
 * working map, for CreateReferenceMetadataStep to use.
 */
public class SetReferencedDestinationBigQueryDatasetInWorkingMapStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final ControlledBigQueryDatasetResource sourceDataset;
  private final ReferencedResourceService referencedResourceService;
  private final CloningInstructions resolvedCloningInstructions;

  public SetReferencedDestinationBigQueryDatasetInWorkingMapStep(
      SamService samService,
      AuthenticatedUserRequest userRequest,
      ControlledBigQueryDatasetResource sourceDataset,
      ReferencedResourceService referencedResourceService,
      CloningInstructions resolvedCloningInstructions) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.sourceDataset = sourceDataset;
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
        resolvedCloningInstructions == CloningInstructions.COPY_REFERENCE,
        "CloningInstructions must be COPY_REFERENCE");
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

    var userEmail =
        SamRethrow.onInterrupted(
            () -> samService.getUserEmailFromSam(userRequest), "Get user status info from sam");
    ReferencedBigQueryDatasetResource destinationDatasetResource =
        sourceDataset
            .buildReferencedClone(
                destinationWorkspaceId,
                destinationResourceId,
                destinationFolderId,
                resourceName,
                description,
                userEmail)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);

    workingMap.put(
        ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, destinationDatasetResource);
    workingMap.put(ResourceKeys.RESOURCE_TYPE, destinationDatasetResource.getResourceType());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
