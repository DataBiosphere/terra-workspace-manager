package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.landingzone.db.LandingZoneDao;
import bio.terra.landingzone.db.model.LandingZone;
import bio.terra.landingzone.library.landingzones.deployment.LandingZoneTagKeys;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.data.CreateStorageAccountRequestData;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import java.util.UUID;

/**
 * Create quasi landing zone for testing.
 *
 * <p>We are cheating here when creating landing zone. One of the main reason is that LZ service API
 * doesn't support deletion of landing zone. This functionality is under construction. In this
 * particular case we don't need real landing zone, because everything we need is shared storage
 * account as part of landing zone. So, here we use LZ low-level api to create landing zone: -
 * register landing zone in LZ database - create shared storage account It will allow us to clean up
 * created resource and test functionality with quasi landing zone.
 *
 * <p>This class should go once delete landing zone operation is available
 */
public class TestLandingZoneManager {
  private final AzureCloudContextService azureCloudContextService;
  private final LandingZoneDao landingZoneDao;
  private final CrlService crlService;
  private final AzureConfiguration azureConfig;
  private final UUID workspaceUuid;
  private final AzureCloudContext azureCloudContext;
  private final StorageManager storageManager;

  public TestLandingZoneManager(
      AzureCloudContextService azureCloudContextService,
      LandingZoneDao landingZoneDao,
      CrlService crlService,
      AzureConfiguration azureConfig,
      UUID workspaceUuid) {
    this.azureCloudContextService = azureCloudContextService;
    this.landingZoneDao = landingZoneDao;
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.workspaceUuid = workspaceUuid;

    azureCloudContext = azureCloudContextService.getAzureCloudContext(workspaceUuid).orElse(null);
    assertNotNull(azureCloudContext, "Azure cloud context should exist. Check configuration.");

    storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);
  }

  public void createLandingZoneWithSharedStorageAccount(
      UUID landingZoneId, String storageAccountName, String region) {
    createLandingZoneDbRecord(landingZoneId);
    createStorageAccount(storageAccountName, region, landingZoneId);
  }

  public void deleteLandingZoneWithSharedStorageAccount(
      UUID landingZoneId, String azureResourceGroup, String storageAccountName) {
    landingZoneDao.deleteLandingZone(landingZoneId);

    storageManager.storageAccounts().deleteByResourceGroup(azureResourceGroup, storageAccountName);
  }

  public void createLandingZoneWithoutResources(UUID landingZoneId) {
    createLandingZoneDbRecord(landingZoneId);
  }

  public void deleteLandingZoneWithoutResources(UUID landingZoneId) {
    landingZoneDao.deleteLandingZone(landingZoneId);
  }

  private void createLandingZoneDbRecord(UUID landingZoneId) {
    String definition = "QuasiLandingZoneWithSharedStorageAccount";
    String version = "v1";
    // create record in LZ database
    landingZoneDao.createLandingZone(
        LandingZone.builder()
            .landingZoneId(landingZoneId)
            .definition(definition)
            .version(version)
            .description(String.format("Definition:%s Version:%s", definition, version))
            .displayName(definition)
            .properties(null)
            .resourceGroupId(azureCloudContext.getAzureResourceGroupId())
            .subscriptionId(azureCloudContext.getAzureSubscriptionId())
            .tenantId(azureCloudContext.getAzureTenantId())
            .build());
  }

  private StorageAccount createStorageAccount(
      String storageAccountName, String region, UUID landingZoneId) {
    StorageAccount storageAccount =
        storageManager
            .storageAccounts()
            .define(storageAccountName)
            .withRegion(region)
            .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
            .withHnsEnabled(true)
            .withTag("workspaceId", workspaceUuid.toString())
            .withTag(LandingZoneTagKeys.LANDING_ZONE_ID.toString(), landingZoneId.toString())
            .withTag(
                LandingZoneTagKeys.LANDING_ZONE_PURPOSE.toString(),
                ResourcePurpose.SHARED_RESOURCE.toString())
            .create(
                Defaults.buildContext(
                    CreateStorageAccountRequestData.builder()
                        .setName(storageAccountName)
                        .setRegion(Region.fromName(region))
                        .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                        .build()));
    return storageAccount;
  }
}
