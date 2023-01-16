package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.storage.common.StorageSharedKeyCredential;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StorageAccountKeyProvider {
  private final AzureCloudContextService azureCloudContextService;
  private final CrlService crlService;
  private final AzureConfiguration azureConfiguration;
  private final Map<AzureStorageAccountCacheKey, String> storageAccountKeyCache;

  @Autowired
  public StorageAccountKeyProvider(
      AzureCloudContextService azureCloudContextService1,
      CrlService crlService,
      AzureConfiguration azureConfiguration) {
    this.azureCloudContextService = azureCloudContextService1;
    this.crlService = crlService;
    this.azureConfiguration = azureConfiguration;
    this.storageAccountKeyCache =
        Collections.synchronizedMap(new PassiveExpiringMap<>(24, TimeUnit.HOURS));
  }

  public StorageSharedKeyCredential getStorageAccountKey(
      UUID workspaceUuid, String storageAccountName) {
    AzureCloudContext azureCloudContext =
        azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid);

    // cache storage account keys to avoid reaching out to azure for every request for a sas
    var storageAccountCacheKey =
        new AzureStorageAccountCacheKey(
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            storageAccountName);

    var key =
        storageAccountKeyCache.computeIfAbsent(
            storageAccountCacheKey,
            v -> {
              StorageManager storageManager =
                  crlService.getStorageManager(azureCloudContext, azureConfiguration);
              StorageAccount storageAccount =
                  storageManager
                      .storageAccounts()
                      .getByResourceGroup(
                          azureCloudContext.getAzureResourceGroupId(), storageAccountName);

              return storageAccount.getKeys().get(0).value();
            });
    return new StorageSharedKeyCredential(storageAccountName, key);
  }
}
