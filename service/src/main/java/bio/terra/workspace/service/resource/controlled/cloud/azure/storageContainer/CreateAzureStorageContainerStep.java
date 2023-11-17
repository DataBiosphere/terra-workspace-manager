package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.storage.data.CreateStorageContainerRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.PublicAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureStorageContainerStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateAzureStorageContainerStep.class);
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
    final StorageManager storageManager =
        crlService.getStorageManager(azureCloudContext, azureConfig);

    // The storage account name is stored by VerifyAzureStorageContainerCanBeCreated.
    // It can be workspace managed storage account or landing zone shared storage account
    final String storageAccountName =
        context.getWorkingMap().get(ControlledResourceKeys.STORAGE_ACCOUNT_NAME, String.class);
    if (storageAccountName == null) {
      logger.error(
          "The storage account name has not been added to the working map. "
              + "VerifyAzureStorageContainerCanBeCreated must be executed first.");
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    try {
      storageManager
          .blobContainers()
          .defineContainer(resource.getStorageContainerName())
          .withExistingStorageAccount(
              azureCloudContext.getAzureResourceGroupId(), storageAccountName)
          .withPublicAccess(PublicAccess.NONE)
          .withMetadata("workspaceId", resource.getWorkspaceId().toString())
          .withMetadata("resourceId", resource.getResourceId().toString())
          .create(
              Defaults.buildContext(
                  CreateStorageContainerRequestData.builder()
                      .setStorageContainerName(resource.getStorageContainerName())
                      .setStorageAccountName(storageAccountName)
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                      .setTenantId(azureCloudContext.getAzureTenantId())
                      .build()));
    } catch (ManagementException e) {
      logger.error(
          "Failed to create the Azure storage container '{}'. Error Code: {}",
          resource.getStorageContainerName(),
          e.getValue().getCode(),
          e);

      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  /**
   * Deletes the storage container if the container is available. If the storage container is
   * available and deletes fails, the failure is considered fatal and must looked into it.
   *
   * @param context context
   * @return Step result.
   * @throws InterruptedException InterruptedException
   */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    final StorageManager storageManager =
        crlService.getStorageManager(azureCloudContext, azureConfig);
    final String storageAccountName =
        context.getWorkingMap().get(ControlledResourceKeys.STORAGE_ACCOUNT_NAME, String.class);
    if (storageAccountName == null) {
      logger.warn(
          "Deletion of the storage container is not required. "
              + "Parent storage account was not found in the create.");
      return StepResult.getStepResultSuccess();
    }

    try {
      storageManager
          .storageAccounts()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), storageAccountName);
    } catch (ManagementException ex) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          ex, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.warn(
            "Deletion of the storage container is not required. Parent storage account does not exist. {}",
            storageAccountName);
        return StepResult.getStepResultSuccess();
      }
      logger.error(
          "Attempt to retrieve parent Azure storage account before deleting container failed on this try: '{}'. Error Code: {}.",
          resource.getStorageContainerName(),
          ex.getValue().getCode(),
          ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
    try {
      storageManager
          .blobContainers()
          .get(
              azureCloudContext.getAzureResourceGroupId(),
              storageAccountName,
              resource.getStorageContainerName());
    } catch (ManagementException ex) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          ex, AzureManagementExceptionUtils.CONTAINER_NOT_FOUND)) {
        logger.warn(
            "Deletion of the storage container is not required. Storage container does not exist. {}",
            resource.getStorageContainerName());
        return StepResult.getStepResultSuccess();
      }
      logger.error(
          "Attempt to retrieve Azure storage container before deleting it failed on this try: '{}'. Error Code: {}.",
          resource.getStorageContainerName(),
          ex.getValue().getCode(),
          ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }

    try {
      logger.warn(
          "Attempting to delete storage container '{}' in account '{}'",
          resource.getStorageContainerName(),
          storageAccountName);
      storageManager
          .blobContainers()
          .delete(
              azureCloudContext.getAzureResourceGroupId(),
              storageAccountName,
              resource.getStorageContainerName());
      logger.warn(
          "Successfully deleted storage container '{}' in account '{}'",
          resource.getStorageContainerName(),
          storageAccountName);
      return StepResult.getStepResultSuccess();
    } catch (ManagementException ex) {
      logger.error(
          "Attempt to delete Azure storage container failed: '{}'. Error Code: {}.",
          resource.getStorageContainerName(),
          ex.getValue().getCode(),
          ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, ex);
    }
  }
}
