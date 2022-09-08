package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.data.CreateStorageAccountRequestData;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpClient;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobCorsRule;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureStorageStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureStorageStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageResource resource;

  public CreateAzureStorageStep(
          AzureConfiguration azureConfig,
          CrlService crlService,
          ControlledAzureStorageResource resource) {
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
    StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    try {
      var acct =
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
                                              .build()));

      StorageSharedKeyCredential storageKey =
              new StorageSharedKeyCredential(acct.name(), acct.getKeys().get(0).value());
      BlobServiceClient svcClient =
              new BlobServiceClientBuilder()
                      .credential(storageKey)
                      .endpoint(acct.endPoints().primary().blob())
                      .httpClient(HttpClient.createDefault())
                      .buildClient();
      var props = svcClient.getProperties();

      // todo how do we managed the list of allowed origins?
      // we may be able to wildcard at least *.terra.bio
      var corsRule =
              new BlobCorsRule()
                      .setAllowedOrigins("https://example.com")
                      .setAllowedMethods("GET");
      var corsRules = props.getCors();
      corsRules.add(corsRule);
      props.setCors(corsRules);
      svcClient.setProperties(props);

    } catch (ManagementException e) {
      logger.error(
              "Failed to create the Azure Storage account with the name: {} Error Code: {}",
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
    final StorageManager storageManager =
            crlService.getStorageManager(azureCloudContext, azureConfig);

    // If the storage account does not exist (isAvailable == true)
    // then return success.
    if (storageManager
            .storageAccounts()
            .checkNameAvailability(resource.getStorageAccountName())
            .isAvailable()) {
      logger.warn(
              "Deletion of the storage account is not required. Storage account does not exist. {}",
              resource.getStorageAccountName());
      return StepResult.getStepResultSuccess();
    }

    try {
      logger.warn("Attempting to delete storage account: {}", resource.getStorageAccountName());
      storageManager
              .storageAccounts()
              .deleteByResourceGroup(
                      azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName());
      logger.warn("Successfully deleted storage account: {}", resource.getStorageAccountName());
    } catch (ManagementException e) {
      logger.error("Failed to delete storage account: {}", resource.getStorageAccountName());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
  }
}
