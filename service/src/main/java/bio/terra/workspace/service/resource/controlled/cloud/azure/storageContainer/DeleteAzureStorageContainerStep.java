package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure Storage Con resource.
 */
public class DeleteAzureStorageContainerStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureStorageContainerStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageContainerResource resource;
  private final ResourceDao resourceDao;

  public DeleteAzureStorageContainerStep(AzureConfiguration azureConfig, CrlService crlService,
                                         ResourceDao resourceDao, ControlledAzureStorageContainerResource resource ) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    final StorageManager manager = crlService.getStorageManager(azureCloudContext, azureConfig);

    try {
      WsmResource wsmResource = resourceDao.getResource(resource.getWorkspaceId(), resource.getStorageAccountId());
      ControlledAzureStorageResource storageAccount = wsmResource.castToControlledResource().castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

      logger.info("Attempting to delete storage container '{}' in account '{}'", resource.getStorageContainerName(), storageAccount.getStorageAccountName());
      manager.blobContainers().delete(
              azureCloudContext.getAzureResourceGroupId(), storageAccount.getStorageAccountName(), resource.getStorageContainerName());
      return StepResult.getStepResultSuccess();
    } catch (ResourceNotFoundException resourceNotFoundException) { // Thrown by resourceDao.getResource
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, resourceNotFoundException);
    } catch (ManagementException ex) {
      logger.warn(
          "Attempt to delete Azure Storage Container failed on this try: " + resource.getStorageContainerName(), ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
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
