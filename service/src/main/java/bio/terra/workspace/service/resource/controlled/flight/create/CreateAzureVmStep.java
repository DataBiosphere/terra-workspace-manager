package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateNetworkRequestData;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateNetworkSecurityGroupRequestData;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateVirtualMachineRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureVmResource;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.NetworkSecurityGroup;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import com.azure.resourcemanager.network.models.SecurityRuleProtocol;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureVmStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateAzureIpStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureVmResource resource;
  private final AzureCloudContext azureCloudContext;
  private final ResourceDao resourceDao;

  public CreateAzureVmStep(
      AzureConfiguration azureConfig,
      AzureCloudContext azureCloudContext,
      CrlService crlService,
      ControlledAzureVmResource resource,
      ResourceDao resourceDao) {
    this.azureConfig = azureConfig;
    this.azureCloudContext = azureCloudContext;
    this.crlService = crlService;
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    final ControlledAzureIpResource ipResource =
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getIpId())
            .castToControlledResource()
            .castToAzureIpResource();

    final ControlledAzureDiskResource diskResource =
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getDiskId())
            .castToControlledResource()
            .castToAzureDiskResource();

    // TODO network: remove once networks are a controlled resource
    final String name = getAzureName("network");
    final String subnetName = getAzureName("subnet");
    final String addressCidr = "192.168.0.0/16";
    final String subnetAddressCidr = "192.168.1.0/24";

    try {
      Network network = createNetwork(name, subnetName, addressCidr, subnetAddressCidr);

      Disk existingAzureDisk =
          computeManager
              .disks()
              .getByResourceGroup(
                  azureCloudContext.getAzureResourceGroupId(), diskResource.getDiskName());

      PublicIpAddress existingAzureIp =
          computeManager
              .networkManager()
              .publicIpAddresses()
              .getByResourceGroup(
                  azureCloudContext.getAzureResourceGroupId(), ipResource.getIpName());

      String imageUrl =
          Optional.ofNullable(resource.getVmImageUri())
              .orElse(azureConfig.getCustomDockerImageId());
      computeManager
          .virtualMachines()
          .define(resource.getVmName())
          .withRegion(resource.getRegion())
          .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
          // TODO network:, use a real network
          .withExistingPrimaryNetwork(network)
          .withSubnet(subnetName)
          .withPrimaryPrivateIPAddressDynamic()
          .withExistingPrimaryPublicIPAddress(existingAzureIp)
          // See here for difference between 'specialized' and 'general' LinuxCustomImage, the
          // managed disk storage option being the key factor
          // https://docs.microsoft.com/en-us/azure/virtual-machines/linux/imaging#generalized-and-specialized
          .withSpecializedLinuxCustomImage(resource.getVmImageUri())
          .withExistingDataDisk(existingAzureDisk)
          .withTag("workspaceId", resource.getWorkspaceId().toString())
          .withTag("resourceId", resource.getResourceId().toString())
          .withSize(VirtualMachineSizeTypes.fromString(resource.getVmSize()))
          .create(
              Defaults.buildContext(
                  CreateVirtualMachineRequestData.builder()
                      .setName(resource.getVmName())
                      .setRegion(Region.fromName(resource.getRegion()))
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setNetwork(network)
                      .setSubnetName(subnetName)
                      .setDisk(existingAzureDisk)
                      .setPublicIpAddress(existingAzureIp)
                      .setImage(imageUrl)
                      .build()));
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      // Azure error codes can be found here:
      // https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/common-deployment-errors
      if (StringUtils.equals(e.getValue().getCode(), "Conflict")) {
        logger.info(
            "Azure Vm {} in managed resource group {} already exists",
            resource.getVmName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
        logger.info(
            "Either the disk, ip, or network passed into this createVm does not exist "
                + String.format(
                    "\nResource Group: %s\n\tIp Name: %s\n\tNetwork Name: %s\n\tDisk Name: %s",
                    azureCloudContext.getAzureResourceGroupId(),
                    ipResource.getIpName(),
                    "TODO",
                    diskResource.getDiskName()));
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    try {
      computeManager
          .virtualMachines()
          .deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getVmName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
        logger.info(
            "Azure VM {} in managed resource group {} already deleted",
            resource.getVmName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  // TODO: THIS IS NOT HOW NETWORKS WILL BE CREATED, AND WILL BE REMOVED TO USE A CONTROLLED
  // RESOURCE ONCE THAT WORK IS DONE (SEPARATE TICKET)
  // Most of this is taken from this very helpful snippet
  // https://github.com/Azure-Samples/network-java-manage-virtual-network/blob/master/src/main/java/com/azure/resourcemanager/network/samples/ManageVirtualNetwork.java
  private Network createNetwork(
      String networkName,
      String subnetName,
      String addressSpaceCidr,
      String subnetAddressSpaceCidr) {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    NetworkSecurityGroup subnetNsg =
        computeManager
            .networkManager()
            .networkSecurityGroups()
            .define(subnetName)
            .withRegion(resource.getRegion())
            .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
            .withTag("workspaceId", resource.getWorkspaceId().toString())
            .withTag("resourceId", resource.getResourceId().toString())
            .defineRule("AllowHttpInComing")
            .allowInbound()
            .fromAddress("INTERNET")
            .fromAnyPort()
            .toAnyAddress()
            .toPort(8080)
            .withProtocol(SecurityRuleProtocol.TCP)
            .attach()
            .create(
                Defaults.buildContext(
                    CreateNetworkSecurityGroupRequestData.builder()
                        .setName(networkName)
                        .setRegion(Region.fromName(resource.getRegion()))
                        .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                        .setRules(Collections.emptyList())
                        .build()));

    return computeManager
        .networkManager()
        .networks()
        .define(networkName)
        .withRegion(resource.getRegion())
        .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
        .withTag("workspaceId", resource.getWorkspaceId().toString())
        .withTag("resourceId", resource.getResourceId().toString())
        .withAddressSpace(addressSpaceCidr)
        .defineSubnet(subnetName)
        .withAddressPrefix(subnetAddressSpaceCidr)
        .withExistingNetworkSecurityGroup(subnetNsg)
        .attach()
        .create(
            Defaults.buildContext(
                CreateNetworkRequestData.builder()
                    .setName(networkName)
                    .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                    .setRegion(Region.fromName(resource.getRegion()))
                    .setSubnetName(subnetName)
                    .setNetworkSecurityGroup(subnetNsg)
                    .setAddressPrefix(subnetAddressSpaceCidr)
                    .setAddressSpaceCidr(addressSpaceCidr)
                    .build()));
  }

  // TODO: delete me once networks are handled properly
  private static String getAzureName(String tag) {
    final String id = UUID.randomUUID().toString().substring(0, 6);
    return String.format("wsm-stub-%s-%s", tag, id);
  }
}
