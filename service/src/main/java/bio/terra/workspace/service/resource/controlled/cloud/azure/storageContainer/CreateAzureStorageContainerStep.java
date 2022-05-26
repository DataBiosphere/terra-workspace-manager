package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.data.CreateStorageAccountRequestData;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.resourcemanager.data.CreateStorageContainerRequestData;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.BlobContainer;
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
          //.withTag("resourceId", resource.getResourceId().toString()) Maybe add metdata?
          .withPublicAccess(PublicAccess.NONE).
              create(  // TODO: what about access?
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
   * Deletes the storage account if the account is available. If the storage account is available
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


    final BlobContainer container = storageManager.blobContainers().get(
            azureCloudContext.getAzureResourceGroupId(),
            resource.getStorageAccountName(),
            resource.getStorageContainerName()
    );

//    // If the storage account does not exist (isAvailable == true)
//    // then return success.
//    if (storageManager
//        .storageAccounts()
//        .checkNameAvailability(resource.getStorageAccountName())
//        .isAvailable()) {
//      logger.warn(
//          "Deletion of the storage account is not required. Storage account does not exist. {}",
//          resource.getStorageAccountName());
      return StepResult.getStepResultSuccess();
    }

//    try {
//      logger.warn("Attempting to delete storage account: {}", resource.getStorageAccountName());
//      storageManager
//          .storageAccounts()
//          .deleteByResourceGroup(
//              azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName());
//      logger.warn("Successfully deleted storage account: {}", resource.getStorageAccountName());
//    } catch (ManagementException e) {
//      logger.error("Failed to delete storage account: {}", resource.getStorageAccountName());
//      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
//    }
//
//    return StepResult.getStepResultSuccess();
//  }
}
