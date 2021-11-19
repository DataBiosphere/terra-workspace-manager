package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.flight.create.azure.resourcemanager.storage.data.CreateStorageAccountRequestData;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureStorageStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureStorageStep.class);
  public static final String CREATED_STORAGE_ACCOUNT_ID = "CREATED_STORAGE_ACCOUNT_ID";
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageResource resource;
  private final AzureCloudContext azureCloudContext;

  public CreateAzureStorageStep(
      AzureConfiguration azureConfig,
      AzureCloudContext azureCloudContext,
      CrlService crlService,
      ControlledAzureStorageResource resource) {
    this.azureConfig = azureConfig;
    this.azureCloudContext = azureCloudContext;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    try {
      String storageAccountId =
          storageManager
              .storageAccounts()
              .define(resource.getStorageAccountName())
              .withRegion(resource.getRegion())
              .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
              .withHnsEnabled(true)
              .withTag("workspaceId", resource.getWorkspaceId().toString())
              .withTag("resourceId", resource.getResourceId().toString())
              .create(
                  Defaults.buildContext(
                      CreateStorageAccountRequestData.builder()
                          .setName(resource.getStorageAccountName())
                          .setRegion(Region.fromName(resource.getRegion()))
                          .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                          .build()))
              .id();

      setStorageAccountIdCreatedInCurrentContext(context, storageAccountId);

    } catch (ManagementException e) {
      logger.error(
          "Failed to create the Azure Storage account with the name: {} Error Code: {}",
          resource.getStorageAccountName(),
          e.getValue().getCode(),
          e);

      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
  }

  /**
   * Deletes the storage account if the current flight created it. This method is not canonically
   * idempotent as its behavior depends on the flight the context. This approach is intended to: -
   * Avoid unwanted deletion if two or more concurrent requests pass the get step and one or more
   * creation requests fail. - Enforce deletion of the storage account only if created successfully
   * but the do step failed.
   *
   * @param context
   * @return Step result.
   * @throws InterruptedException
   */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    String storageAccountId = getStorageAccountIdCreatedInCurrentContext(context);
    if (storageAccountId != null) {
      try {
        logger.warn("Attempting to delete storage account: {}", storageAccountId);
        storageManager.storageAccounts().deleteById(storageAccountId);
        logger.warn("Successfully deleted storage account: {}", storageAccountId);
      } catch (ManagementException e) {
        logger.error("Failed to delete storage account: {}", storageAccountId);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  private String getStorageAccountIdCreatedInCurrentContext(FlightContext context) {
    return context.getWorkingMap().get(CREATED_STORAGE_ACCOUNT_ID, String.class);
  }

  private void setStorageAccountIdCreatedInCurrentContext(
      FlightContext context, String storageAccountId) {
    context.getWorkingMap().put(CREATED_STORAGE_ACCOUNT_ID, storageAccountId);
  }
}
