package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.Optional;
import java.util.UUID;

public class UpdateControlledResourceMetadataStep implements Step {

  private final ResourceDao resourceDao;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;
  private final ControlledResource resource;
  private final UUID resourceId;
  private final UUID workspaceUuid;

  public UpdateControlledResourceMetadataStep(
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      ResourceDao resourceDao,
      ControlledResource resource) {
    this.resourceDao = resourceDao;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
    this.resource = resource;
    this.resourceId = resource.getResourceId();
    this.workspaceUuid = resource.getWorkspaceId();
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntries(
        flightContext.getInputParameters(),
        ResourceKeys.RESOURCE_NAME,
        ResourceKeys.RESOURCE_DESCRIPTION);
    final FlightMap inputParameters = flightContext.getInputParameters();
    final String resourceName = inputParameters.get(ResourceKeys.RESOURCE_NAME, String.class);
    final String resourceDescription =
        inputParameters.get(ResourceKeys.RESOURCE_DESCRIPTION, String.class);

    // Cloning storage is in different update parameters class depending on resource type.
    final CloningInstructions cloningInstructions;
    if (WsmResourceType.CONTROLLED_GCP_GCS_BUCKET == resource.getResourceType()) {
      final var bucketUpdateParameters =
          inputParameters.get(
              ControlledResourceKeys.UPDATE_PARAMETERS, ApiGcpGcsBucketUpdateParameters.class);
      cloningInstructions =
          CloningInstructions.fromApiModel(
              Optional.ofNullable(bucketUpdateParameters)
                  .map(ApiGcpGcsBucketUpdateParameters::getCloningInstructions)
                  .orElse(null));
    } else if (WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET == resource.getResourceType()) {
      final var datasetUpdateParameters =
          inputParameters.get(
              ControlledResourceKeys.UPDATE_PARAMETERS,
              ApiGcpBigQueryDatasetUpdateParameters.class);
      cloningInstructions =
          CloningInstructions.fromApiModel(
              Optional.ofNullable(datasetUpdateParameters)
                  .map(ApiGcpBigQueryDatasetUpdateParameters::getCloningInstructions)
                  .orElse(null));
    } else {
      cloningInstructions = null; // don't change the value
    }

    boolean updated =
        controlledResourceMetadataManager.updateControlledResourceMetadata(
            workspaceUuid, resourceId, resourceName, resourceDescription, cloningInstructions);
    if (!updated) {
      throw new RetryException(
          "Failed to update controlled resource metadata for resource %s in workspace %s"
              .formatted(resourceId, workspaceUuid));
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String previousName = workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_NAME, String.class);
    final String previousDescription =
        workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, String.class);
    final var previousCloningInstructions =
        workingMap.get(ResourceKeys.PREVIOUS_CLONING_INSTRUCTIONS, CloningInstructions.class);
    resourceDao.updateResource(
        workspaceUuid,
        resourceId,
        previousName,
        previousDescription,
        null,
        previousCloningInstructions);
    return StepResult.getStepResultSuccess();
  }
}
