package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.landingzone.db.LandingZoneDao;
import bio.terra.landingzone.db.model.LandingZoneRecord;
import bio.terra.landingzone.library.landingzones.deployment.LandingZoneTagKeys;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.data.CreateStorageAccountRequestData;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * This class allows creating 2 different landing zones. It is created for integration test purposes
 * only. It simplifies process of creation/deletion of a landing zone with custom resources without
 * using high-level LZ API. This class might be improved/refactored in future if we need to handle
 * wide range of different LZs. Currently, it has 2 sets of symmetrical create/delete operations for
 * 2 different LZs.
 *
 * <p>We are cheating here when creating landing zone. One of the main reason is that LZ service has
 * limited number of available landing zones for creation and also LZ service doesn't have LZ delete
 * operation implemented yet. In this particular case we don't need to use high-level LZ service
 * API. Here we mimic landing zone creation/deletion process by using LZ low-level api:
 *
 * <p>-register landing zone in LZ database
 *
 * <p>-create shared storage account with appropriate tags
 */
public class TestLandingZoneManager {
  private final AzureCloudContextService azureCloudContextService;
  private final LandingZoneDao landingZoneDao;
  private final CrlService crlService;
  private final AzureConfiguration azureConfig;
  private final UUID workspaceUuid;
  private final AzureCloudContext azureCloudContext;
  private final StorageManager storageManager;
  private final WorkspaceDao workspaceDao;
  private final ComputeManager computeManager;

  public TestLandingZoneManager(
      AzureCloudContextService azureCloudContextService,
      LandingZoneDao landingZoneDao,
      WorkspaceDao workspaceDao,
      CrlService crlService,
      AzureConfiguration azureConfig,
      UUID workspaceUuid) {
    this.azureCloudContextService = azureCloudContextService;
    this.landingZoneDao = landingZoneDao;
    this.workspaceDao = workspaceDao;
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.workspaceUuid = workspaceUuid;

    azureCloudContext = azureCloudContextService.getAzureCloudContext(workspaceUuid).orElse(null);
    assertNotNull(azureCloudContext, "Azure cloud context should exist. Make sure it was created.");

    storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);
    computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
  }

  public StorageAccount createLandingZoneWithSharedStorageAccount(
      UUID landingZoneId, UUID workspaceId, String storageAccountName, String region) {
    createLandingZoneDbRecord(landingZoneId, workspaceId);
    return createStorageAccount(storageAccountName, region, landingZoneId);
  }

  public void deleteLandingZoneWithSharedStorageAccount(
      UUID landingZoneId, String azureResourceGroup, String storageAccountName) {
    landingZoneDao.deleteLandingZone(landingZoneId);

    storageManager.storageAccounts().deleteByResourceGroup(azureResourceGroup, storageAccountName);
  }

  public Network createLandingZoneWithComputeSubnet(
      UUID landingZoneId, UUID workspaceId, String networkName, String region) {
    createLandingZoneDbRecord(landingZoneId, workspaceId);
    return createNetworkWithComputeSubnet(networkName, region, landingZoneId);
  }

  public void deleteLandingZoneWithComputeSubnet(
      UUID landingZoneId, String azureResourceGroup, String networkName) {
    landingZoneDao.deleteLandingZone(landingZoneId);

    computeManager
        .networkManager()
        .networks()
        .deleteByResourceGroup(azureResourceGroup, networkName);
  }

  public void createLandingZoneWithoutResources(UUID landingZoneId, UUID workspaceId) {
    createLandingZoneDbRecord(landingZoneId, workspaceId);
  }

  public void deleteLandingZoneWithoutResources(UUID landingZoneId) {
    landingZoneDao.deleteLandingZone(landingZoneId);
  }

  private void createLandingZoneDbRecord(UUID landingZoneId, UUID workspaceId) {
    String definition = "QuasiLandingZoneWithSharedStorageAccount";
    String version = "v1";
    // create record in LZ database
    var workspace = workspaceDao.getWorkspace(workspaceId);
    landingZoneDao.createLandingZone(
        LandingZoneRecord.builder()
            .landingZoneId(landingZoneId)
            .definition(definition)
            .version(version)
            .description(String.format("Definition:%s Version:%s", definition, version))
            .displayName(definition)
            .properties(null)
            .resourceGroupId(azureCloudContext.getAzureResourceGroupId())
            .subscriptionId(azureCloudContext.getAzureSubscriptionId())
            .tenantId(azureCloudContext.getAzureTenantId())
            .billingProfileId(UUID.fromString(workspace.getSpendProfileId().get().getId()))
            .createdDate(OffsetDateTime.of(LocalDate.now(), LocalTime.now(), ZoneOffset.UTC))
            .build());
  }

  private StorageAccount createStorageAccount(
      String storageAccountName, String region, UUID landingZoneId) {
    return storageManager
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
  }

  private Network createNetworkWithComputeSubnet(
      String networkName, String region, UUID landingZoneId) {
    final String subnetName = "COMPUTE_SUBNET";

    return computeManager
        .networkManager()
        .networks()
        .define(networkName)
        .withRegion(region)
        .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
        .withAddressSpace("10.1.0.0/27")
        .withSubnet(subnetName, "10.1.0.24/29")
        .withTag("workspaceId", workspaceUuid.toString())
        .withTag(LandingZoneTagKeys.LANDING_ZONE_ID.toString(), landingZoneId.toString())
        .withTag(SubnetResourcePurpose.WORKSPACE_COMPUTE_SUBNET.toString(), subnetName)
        .create();
  }
}
