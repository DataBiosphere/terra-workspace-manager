package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccountKey;
import com.azure.storage.common.StorageSharedKeyCredential;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StorageAccountKeyProvider {
  private final AzureCloudContextService azureCloudContextService;
  private final CrlService crlService;
  private final AzureConfiguration azureConfiguration;

  @Autowired
  public StorageAccountKeyProvider(
      AzureCloudContextService azureCloudContextService1,
      CrlService crlService,
      AzureConfiguration azureConfiguration) {
    this.azureCloudContextService = azureCloudContextService1;
    this.crlService = crlService;
    this.azureConfiguration = azureConfiguration;
  }

  public StorageSharedKeyCredential getStorageAccountKey(
      UUID workspaceUuid, String storageAccountName) {
    AzureCloudContext azureCloudContext =
        azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid);
    StorageManager storageManager =
        crlService.getStorageManager(azureCloudContext, azureConfiguration);
    StorageAccount storageAccount =
        storageManager
            .storageAccounts()
            .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), storageAccountName);

    StorageAccountKey key = storageAccount.getKeys().get(0);
    return new StorageSharedKeyCredential(storageAccountName, key.value());
  }
}
