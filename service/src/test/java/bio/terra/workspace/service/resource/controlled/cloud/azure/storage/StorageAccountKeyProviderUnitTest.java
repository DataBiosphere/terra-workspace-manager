package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseSpringBootAzureUnitTest;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccountKey;
import com.azure.resourcemanager.storage.models.StorageAccounts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

public class StorageAccountKeyProviderUnitTest extends BaseSpringBootAzureUnitTest {

  @Test
  public void getStorageAccountKey() {
    var azureCloudContextService = mock(AzureCloudContextService.class);
    var crlService = mock(CrlService.class);
    var storageManager = mock(StorageManager.class);
    var azureConfiguration = new AzureConfiguration();
    var azureCloudContext =
        new AzureCloudContext(
            new AzureCloudContextFields("fake", "fake", "fake"),
            new CloudContextCommonFields(
                new SpendProfileId("fake"),
                WsmResourceState.READY,
                /* flightId= */ null,
                /* error= */ null));
    when(azureCloudContextService.getRequiredAzureCloudContext(any()))
        .thenReturn(azureCloudContext);
    when(crlService.getStorageManager(
            ArgumentMatchers.eq(azureCloudContext), ArgumentMatchers.eq(azureConfiguration)))
        .thenReturn(storageManager);
    var storageAccounts = mock(StorageAccounts.class);
    var storageAccount = mock(StorageAccount.class);
    when(storageManager.storageAccounts()).thenReturn(storageAccounts);
    when(storageAccounts.getByResourceGroup(any(), any())).thenReturn(storageAccount);
    var key = mock(StorageAccountKey.class);
    when(key.value()).thenReturn("fake_key");
    when(storageAccount.getKeys()).thenReturn(List.of(key));
    var storageAccountKeyProvider =
        new StorageAccountKeyProvider(azureCloudContextService, crlService, azureConfiguration);

    var result = storageAccountKeyProvider.getStorageAccountKey(UUID.randomUUID(), "fake_account");

    assertEquals(result.getAccountName(), "fake_account");
  }
}
