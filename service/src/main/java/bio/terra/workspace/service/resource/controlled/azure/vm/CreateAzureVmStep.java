package bio.terra.workspace.service.resource.controlled.azure.vm;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateVirtualMachineRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.azure.ip.CreateAzureIpStep;
import bio.terra.workspace.service.resource.controlled.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.PublicIpAddress;
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

    final ControlledAzureNetworkResource networkResource =
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getNetworkId())
            .castToControlledResource()
            .castToAzureNetworkResource();

    try {
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

      Network existingNetwork =
          computeManager
              .networkManager()
              .networks()
              .getByResourceGroup(
                  azureCloudContext.getAzureResourceGroupId(), networkResource.getNetworkName());

      computeManager
          .virtualMachines()
          .define(resource.getVmName())
          .withRegion(resource.getRegion())
          .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
          .withExistingPrimaryNetwork(existingNetwork)
          .withSubnet(networkResource.getSubnetName())
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
                      .setTenantId(azureCloudContext.getAzureTenantId())
                      .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setNetwork(existingNetwork)
                      .setSubnetName(networkResource.getSubnetName())
                      .setDisk(existingAzureDisk)
                      .setPublicIpAddress(existingAzureIp)
                      .setImage(resource.getVmImageUri())
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
                    "%nResource Group: %s%n\tIp Name: %s%n\tNetwork Name: %s%n\tDisk Name: %s",
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
}
