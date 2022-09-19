package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.NetworkInterface;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureNetworkInterfaceStep implements Step {
  private static final Logger logger =
          LoggerFactory.getLogger(CreateAzureNetworkInterfaceStep.class);

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  // Network interface is not a freestanding WSM resource. It is tightly coupled to the Vm.
  private final ControlledAzureVmResource resource;
  private final ResourceDao resourceDao;
  private final LandingZoneApiDispatch landingZoneApiDispatch;

  public CreateAzureNetworkInterfaceStep(
          AzureConfiguration azureConfig,
          CrlService crlService,
          ControlledAzureVmResource resource,
          ResourceDao resourceDao,
          LandingZoneApiDispatch landingZoneApiDispatch) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.resourceDao = resourceDao;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
            context
                    .getWorkingMap()
                    .get(
                            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                            AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    final Optional<ControlledAzureIpResource> ipResource =
            Optional.ofNullable(resource.getIpId())
                    .map(
                            ipId ->
                                    resourceDao
                                            .getResource(resource.getWorkspaceId(), ipId)
                                            .castByEnum(WsmResourceType.CONTROLLED_AZURE_IP));

    String networkInterfaceName = String.format("nic-%s", resource.getVmName());
    try {
      Optional<PublicIpAddress> existingAzureIp =
              ipResource.map(
                      ipRes ->
                              computeManager
                                      .networkManager()
                                      .publicIpAddresses()
                                      .getByResourceGroup(
                                              azureCloudContext.getAzureResourceGroupId(), ipRes.getIpName()));

      NetworkSubnetPair existingNetwork =
              getExistingNetworkResources(azureCloudContext, computeManager.networkManager());

      NetworkInterface networkInterface =
              createNetworkInterface(
                      computeManager,
                      azureCloudContext,
                      networkInterfaceName,
                      existingNetwork.network(),
                      existingNetwork.subnet().name(),
                      existingAzureIp);

      // create vm step will use these later
      context
          .getWorkingMap()
          .put(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY, networkInterface.name());
      context
          .getWorkingMap()
          .put(AzureVmHelper.WORKING_MAP_SUBNET_NAME, existingNetwork.subnet().name());

    } catch (ManagementException e) {
      if (StringUtils.equals(e.getValue().getCode(), "Conflict")) {
        logger.info(
                "Azure Network Interface {} in managed resource group {} already exists",
                networkInterfaceName,
                azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  NetworkSubnetPair getExistingNetworkResources(
          AzureCloudContext azureCloudContext, NetworkManager networkManager) {

    // we are considering the network id to be optional
    if (resource.getNetworkId() == null) {
      return getNetworkResourcesFromLandingZone(azureCloudContext, networkManager);
    }

    final ControlledAzureNetworkResource networkResource =
            resourceDao
                    .getResource(resource.getWorkspaceId(), resource.getNetworkId())
                    .castByEnum(WsmResourceType.CONTROLLED_AZURE_NETWORK);

    return NetworkSubnetPair.createNetworkSubnetPair(
            networkManager,
            azureCloudContext.getAzureResourceGroupId(),
            networkResource.getNetworkName(),
            networkResource.getSubnetName());
  }

  private NetworkSubnetPair getNetworkResourcesFromLandingZone(
          AzureCloudContext azureCloudContext, NetworkManager networkManager) {

    final String lzId = landingZoneApiDispatch.getLandingZoneId(azureCloudContext);

    ApiAzureLandingZoneDeployedResource lzResource =
            landingZoneApiDispatch
                    .listSubnetsWithParentVNetByPurpose(
                            lzId, SubnetResourcePurpose.WORKSPACE_COMPUTE_SUBNET)
                    .stream()
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "The landing zone does not contain the requested resource."));

    return NetworkSubnetPair.createNetworkSubnetPair(
            networkManager, lzResource.getResourceParentId(), lzResource.getResourceName());
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
            context
                    .getWorkingMap()
                    .get(
                            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                            AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    String networkInterfaceName =
            context.getWorkingMap().get(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY, String.class);
    return AzureVmHelper.deleteNetworkInterface(
            azureCloudContext, computeManager, networkInterfaceName);
  }

  private NetworkInterface createNetworkInterface(
          ComputeManager computeManager,
          AzureCloudContext azureCloudContext,
          String networkInterfaceName,
          Network existingNetwork,
          String subnetName,
          Optional<PublicIpAddress> existingAzureIp) {
    var createNicStep =
            computeManager
                    .networkManager()
                    .networkInterfaces()
                    .define(networkInterfaceName)
                    .withRegion(existingNetwork.regionName())
                    .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
                    .withExistingPrimaryNetwork(existingNetwork)
                    .withSubnet(subnetName)
                    .withPrimaryPrivateIPAddressDynamic();
    existingAzureIp.ifPresent(createNicStep::withExistingPrimaryPublicIPAddress);
    return createNicStep.create();
  }
}
