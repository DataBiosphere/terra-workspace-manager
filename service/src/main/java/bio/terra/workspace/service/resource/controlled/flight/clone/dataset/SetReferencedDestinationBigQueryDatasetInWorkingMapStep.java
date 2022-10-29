package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.WorkspaceCloneUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
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

  private final AuthenticatedUserRequest userRequest;
  private final ControlledBigQueryDatasetResource sourceDataset;
  private final ReferencedResourceService referencedResourceService;
  private final CloningInstructions resolvedCloningInstructions;

  public SetReferencedDestinationBigQueryDatasetInWorkingMapStep(
      AuthenticatedUserRequest userRequest,
      ControlledBigQueryDatasetResource sourceDataset,
      ReferencedResourceService referencedResourceService,
      CloningInstructions resolvedCloningInstructions) {
    this.userRequest = userRequest;
    this.sourceDataset = sourceDataset;
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
    final UUID destinationFolderId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_FOLDER_ID, UUID.class);

    ReferencedBigQueryDatasetResource destinationDatasetResource =
        WorkspaceCloneUtils.buildDestinationReferencedBigQueryDatasetFromControlled(
            sourceDataset,
            destinationWorkspaceId,
            destinationResourceId,
            destinationFolderId,
            resourceName,
            description);

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
