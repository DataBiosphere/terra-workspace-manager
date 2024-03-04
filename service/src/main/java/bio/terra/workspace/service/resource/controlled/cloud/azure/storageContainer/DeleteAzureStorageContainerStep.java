package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting a controlled Azure Storage Con resource. */
public class DeleteAzureStorageContainerStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteAzureStorageContainerStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageContainerResource resource;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;

  public DeleteAzureStorageContainerStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      ControlledAzureStorageContainerResource resource,
      WorkspaceService workspaceService) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult deleteResource(FlightContext context) {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    final StorageManager manager = crlService.getStorageManager(azureCloudContext, azureConfig);

    try {
      // Storage container was created based on landing zone shared storage account
      var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
      UUID landingZoneId =
          landingZoneApiDispatch.getLandingZoneId(
              bearerToken, workspaceService.getWorkspace(resource.getWorkspaceId()));
      Optional<ApiAzureLandingZoneDeployedResource> sharedStorageAccount =
          landingZoneApiDispatch.getSharedStorageAccount(bearerToken, landingZoneId);
      if (sharedStorageAccount.isPresent()) {
        StorageAccount storageAccount =
            manager.storageAccounts().getById(sharedStorageAccount.get().getResourceId());
        var storageAccountName = storageAccount.name();
        logger.info(
            "Attempting to delete storage container '{}' in account '{}'",
            resource.getStorageContainerName(),
            storageAccountName);
        manager
            .blobContainers()
            .delete(
                azureCloudContext.getAzureResourceGroupId(),
                storageAccountName,
                resource.getStorageContainerName());
        return StepResult.getStepResultSuccess();
      } else {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new ResourceNotFoundException(
                String.format(
                    "Shared storage account not found in landing zone. Landing zone ID='%s'.",
                    landingZoneId)));
      }
    } catch (LandingZoneNotFoundException lzne) { // Thrown by landingZoneApiDispatch
      // If the landing zone is not present, it's probably because it was removed directly
      return StepResult.getStepResultSuccess();
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure Storage Container resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
