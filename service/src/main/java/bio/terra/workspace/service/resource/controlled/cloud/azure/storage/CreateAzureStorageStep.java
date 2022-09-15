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
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobCorsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureStorageStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureStorageStep.class);
  private static final String CORS_ALLOWED_METHODS = "GET,HEAD,OPTIONS,PUT,PATCH,POST,MERGE,DELETE";
  private static final String CORS_ALLOWED_HEADERS =
      "authorization,content-type,x-app-id,Referer,x-ms-blob-type,x-ms-copy-source,content-length";

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageResource resource;
  private final StorageAccountKeyProvider storageAccountKeyProvider;

  public CreateAzureStorageStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureStorageResource resource,
      StorageAccountKeyProvider storageAccountKeyProvider) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.storageAccountKeyProvider = storageAccountKeyProvider;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    if (azureCloudContext == null) {
      logger.error(
          "Azure cloud context is null for storage account creation. workspace_id = {}",
          resource.getWorkspaceId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    try {
      var createdStorageAccount = createAccount(azureCloudContext, storageManager);
      setupCors(createdStorageAccount);

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

  private StorageAccount createAccount(
      AzureCloudContext azureCloudContext, StorageManager storageManager) {
    return storageManager
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
  }

  private void setupCors(StorageAccount acct) {
    var allowedOrigins = azureConfig.getCorsOrigins();
    if (allowedOrigins == null || allowedOrigins.isBlank()) {
      logger.info("No CORS allowed origins setup, skipping adding for Azure storage account {}", resource.getWorkspaceId());
      return;
    }

    var storageAccountKey =
        storageAccountKeyProvider.getStorageAccountKey(
            resource.getWorkspaceId(), resource.getStorageAccountName());
    BlobServiceClient svcClient =
        new BlobServiceClientBuilder()
            .credential(storageAccountKey)
            .endpoint(acct.endPoints().primary().blob())
            .httpClient(HttpClient.createDefault())
            .buildClient();
    var props = svcClient.getProperties();

    var corsRules = props.getCors();
    var corsRule =
        new BlobCorsRule()
            .setAllowedOrigins(azureConfig.getCorsOrigins())
            .setAllowedMethods(CORS_ALLOWED_METHODS)
            .setAllowedHeaders(CORS_ALLOWED_HEADERS);
    corsRules.add(corsRule);

    props.setCors(corsRules);
    svcClient.setProperties(props);
  }
}
