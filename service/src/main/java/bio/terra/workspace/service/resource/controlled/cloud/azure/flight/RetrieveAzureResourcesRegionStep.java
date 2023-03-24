package bio.terra.workspace.service.resource.controlled.cloud.azure.flight;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_WITHOUT_REGION;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_REGION_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.WORKSPACE_ID_TO_AZURE_CLOUD_CONTEXT_MAP;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.ApiErrorException;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.storage.StorageManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Retrieves the Azure resource's cloud region according to the resource type. */
public class RetrieveAzureResourcesRegionStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(RetrieveAzureResourcesRegionStep.class);

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ResourceDao resourceDao;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final AuthenticatedUserRequest userRequest;

  public RetrieveAzureResourcesRegionStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ResourceDao resourceDao,
      LandingZoneApiDispatch landingZoneApiDispatch,
      AuthenticatedUserRequest userRequest) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resourceDao = resourceDao;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getWorkingMap(),
        CONTROLLED_RESOURCES_WITHOUT_REGION,
        WORKSPACE_ID_TO_AZURE_CLOUD_CONTEXT_MAP);
    List<ControlledResource> controlledResources =
        context.getWorkingMap().get(CONTROLLED_RESOURCES_WITHOUT_REGION, new TypeReference<>() {});
    Map<UUID, String> workspaceIdToAzureCloudContextMap =
        context
            .getWorkingMap()
            .get(WORKSPACE_ID_TO_AZURE_CLOUD_CONTEXT_MAP, new TypeReference<>() {});
    Map<UUID, String> resourceIdToRegionMap = new HashMap<>();
    Map<UUID, String> resourceIdToWorkspaceIdMap = new HashMap<>();
    for (var resource : controlledResources) {
      WsmResourceType resourceType = resource.getResourceType();
      logger.info(
          "Getting cloud region for resource {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      AzureCloudContext azureCloudContext;
      Preconditions.checkState(
          workspaceIdToAzureCloudContextMap.containsKey(resource.getWorkspaceId()),
          "Azure workspace %s must have an azure cloud context",
          resource.getWorkspaceId());
      azureCloudContext =
          AzureCloudContext.deserialize(
              workspaceIdToAzureCloudContextMap.get(resource.getWorkspaceId()));

      switch (resourceType) {
        case CONTROLLED_AZURE_DISK -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAzureDiskRegion(resource.castByEnum(resourceType), azureCloudContext));
        case CONTROLLED_AZURE_IP -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAzureIpRegion(resource.castByEnum(resourceType), azureCloudContext));
        case CONTROLLED_AZURE_NETWORK -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAzureNetworkRegion(resource.castByEnum(resourceType), azureCloudContext));
        case CONTROLLED_AZURE_RELAY_NAMESPACE -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAzureRelayNameSpaceRegion(resource.castByEnum(resourceType), azureCloudContext));
        case CONTROLLED_AZURE_STORAGE_ACCOUNT -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAzureStorageAccountRegion(resource.castByEnum(resourceType), azureCloudContext));
        case CONTROLLED_AZURE_STORAGE_CONTAINER -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAzureStorageContainerRegion(resource.castByEnum(resourceType), azureCloudContext));
        case CONTROLLED_AZURE_VM -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAzureVmRegion(resource.castByEnum(resourceType), azureCloudContext));
        default -> throw new UnsupportedOperationException(
            String.format(
                "resource of type %s is not an azure resource or is a referenced resource",
                resourceType));
      }
    }
    context.getWorkingMap().put(CONTROLLED_RESOURCE_ID_TO_REGION_MAP, resourceIdToRegionMap);
    context
        .getWorkingMap()
        .put(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, resourceIdToWorkspaceIdMap);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // READ-ONLY step, do nothing here.
    return StepResult.getStepResultSuccess();
  }

  private void populateMapsWithResourceIdKey(
      Map<UUID, String> resourceIdToRegionMap,
      Map<UUID, String> resourceIdToWorkspaceIdMap,
      ControlledResource resource,
      @Nullable String region) {
    if (region != null) {
      // NOTE: took this fragment from the Azure code in com.azure.core.management.Region.java
      String normalizedRegion = region.toLowerCase(Locale.ROOT).replace(" ", "");

      UUID resourceId = resource.getResourceId();
      resourceIdToRegionMap.put(resourceId, normalizedRegion);
      resourceIdToWorkspaceIdMap.put(resourceId, resource.getWorkspaceId().toString());
    }
  }

  private String getAzureDiskRegion(
      ControlledAzureDiskResource resource, AzureCloudContext azureCloudContext) {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    try {
      return computeManager
          .disks()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getDiskName())
          .regionName();
    } catch (ApiErrorException e) {
      logger.warn(
          "Cannot get resource group {} for azure disk {}",
          azureCloudContext.getAzureResourceGroupId(),
          resource.getDiskName());
      return null;
    }
  }

  private String getAzureStorageAccountRegion(
      ControlledAzureStorageResource resource, AzureCloudContext azureCloudContext) {
    StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);
    try {
      return storageManager
          .storageAccounts()
          .getByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName())
          .regionName();
    } catch (ApiErrorException e) {
      logger.warn(
          "Cannot get resource group {} for azure storage account {}",
          azureCloudContext.getAzureResourceGroupId(),
          resource.getStorageAccountName());
      return null;
    }
  }

  private String getAzureRelayNameSpaceRegion(
      ControlledAzureRelayNamespaceResource resource, AzureCloudContext azureCloudContext) {
    RelayManager manager = crlService.getRelayManager(azureCloudContext, azureConfig);
    try {
      return manager
          .namespaces()
          .getByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getNamespaceName())
          .regionName();
    } catch (ApiErrorException e) {
      logger.warn(
          "Cannot get resource group {} for azure namespace {}",
          azureCloudContext.getAzureResourceGroupId(),
          resource.getNamespaceName());
      return null;
    }
  }

  private String getAzureStorageContainerRegion(
      ControlledAzureStorageContainerResource resource, AzureCloudContext azureCloudContext) {
    StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);
    if (resource.getStorageAccountId() != null) {
      WsmResource wsmResource =
          resourceDao.getResource(resource.getWorkspaceId(), resource.getStorageAccountId());
      ControlledAzureStorageResource storageAccount =
          wsmResource
              .castToControlledResource()
              .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);
      return getAzureStorageAccountRegion(storageAccount, azureCloudContext);
    }
    UUID landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            new BearerToken(userRequest.getRequiredToken()), resource.getWorkspaceId());
    Optional<ApiAzureLandingZoneDeployedResource> existingSharedStorageAccount =
        landingZoneApiDispatch.getSharedStorageAccount(
            new BearerToken(userRequest.getRequiredToken()), landingZoneId);
    Preconditions.checkState(
        existingSharedStorageAccount.isPresent(),
        "Unexpected: there is no shared storage account in landing zone %s",
        landingZoneId);
    return storageManager
        .storageAccounts()
        .getById(existingSharedStorageAccount.get().getResourceId())
        .regionName();
  }

  private String getAzureVmRegion(
      ControlledAzureVmResource resource, AzureCloudContext azureCloudContext) {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    try {
      return computeManager
          .virtualMachines()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getVmName())
          .regionName();
    } catch (ApiErrorException e) {
      logger.warn(
          "Cannot get resource group {} for azure VM {}",
          azureCloudContext.getAzureResourceGroupId(),
          resource.getVmName());
      return null;
    }
  }

  private String getAzureIpRegion(
      ControlledAzureIpResource resource, AzureCloudContext azureCloudContext) {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    try {
      return computeManager
          .networkManager()
          .publicIpAddresses()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getIpName())
          .regionName();
    } catch (ApiErrorException e) {
      logger.warn(
          "Cannot get resource group {} for azure IP {}",
          azureCloudContext.getAzureResourceGroupId(),
          resource.getIpName());
      return null;
    }
  }

  private String getAzureNetworkRegion(
      ControlledAzureNetworkResource resource, AzureCloudContext azureCloudContext) {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    try {
      return computeManager
          .networkManager()
          .networks()
          .getByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getNetworkName())
          .regionName();
    } catch (ApiErrorException e) {
      logger.warn(
          "Cannot get resource group {} for azure network {}",
          azureCloudContext.getAzureResourceGroupId(),
          resource.getNetworkName());
      return null;
    }
  }
}
