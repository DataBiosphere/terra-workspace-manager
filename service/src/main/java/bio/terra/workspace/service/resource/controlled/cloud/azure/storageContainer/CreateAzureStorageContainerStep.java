package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.resourcemanager.data.CreateStorageContainerRequestData;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.fluent.models.ListContainerItemInner;
import com.azure.resourcemanager.storage.models.PublicAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureStorageContainerStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureStorageContainerStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageContainerResource resource;

  public CreateAzureStorageContainerStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureStorageContainerResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    final StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    try {
      storageManager
          .blobContainers()
          .defineContainer(resource.getStorageContainerName())
          .withExistingStorageAccount(azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName())
          .withPublicAccess(PublicAccess.NONE)
          .withMetadata("workspaceId", resource.getWorkspaceId().toString())
          .withMetadata("resourceId", resource.getResourceId().toString()).
              create(
                Defaults.buildContext(
                  CreateStorageContainerRequestData.builder()
                      .setName(resource.getStorageContainerName())
                      .setStorageAccountName(resource.getStorageAccountName())
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .build()));

    } catch (ManagementException e) {
      logger.error(
          "Failed to create the Azure storage container '{}' with storage account with the name '{}'. Error Code: {}",
          resource.getStorageContainerName(),
          resource.getStorageAccountName(),
          e.getValue().getCode(),
          e);

      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  /**
   * Deletes the storage container if the container is available. If the storage container is available
   * and deletes fails, the failure is considered fatal and must looked into it.
   *
   * @param context
   * @return Step result.
   * @throws InterruptedException
   */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
            context
                    .getWorkingMap()
                    .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    final StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    try {
      storageManager
              .storageAccounts()
              .getByResourceGroup(
                      azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName());
    } catch (ManagementException ex) {
      logger.warn(
              "Deletion of the storage container is not required. Parent storage account does not exist. {}",
              resource.getStorageAccountName());
      return StepResult.getStepResultSuccess();
    }
    final PagedIterable<ListContainerItemInner> existingContainers = storageManager.blobContainers().list(
            azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName()
    );
    ListContainerItemInner existingContainer = null;
    for (ListContainerItemInner item : existingContainers) {
      if (item.name().equals(resource.getStorageContainerName())) {
        existingContainer = item;
        break;
      }
    }
    if (existingContainer == null) {
      logger.warn(
              "Deletion of the storage container is not required. Storage container does not exist. {}",
              resource.getStorageContainerName());
      return StepResult.getStepResultSuccess();
    } else {
      try {
        logger.warn("Attempting to delete storage container '{}' in account '{}'",
                resource.getStorageContainerName(),
                resource.getStorageAccountName()
        );
        storageManager.blobContainers().delete(
                azureCloudContext.getAzureResourceGroupId(),
                resource.getStorageAccountName(),
                resource.getStorageContainerName()
        );
        logger.warn("Successfully deleted storage container '{}' in account '{}'",
                resource.getStorageContainerName(),
                resource.getStorageAccountName()
        );
        return StepResult.getStepResultSuccess();
      } catch (ManagementException ex) {
        logger.error(
                "Attempt to delete Azure Storage Container failed on this try: " + resource.getStorageContainerName(),
                ex
        );
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, ex);
      }
    }
  }
}
