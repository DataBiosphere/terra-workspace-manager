package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateVirtualMachineRequestData;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.AzureVmUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.exception.AzureNetworkInterfaceNameNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.ImageReference;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.NetworkInterface;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class CreateAzureVmStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureVmStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureVmResource resource;
  private final ResourceDao resourceDao;

  public CreateAzureVmStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureVmResource resource,
      ResourceDao resourceDao) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputMap = context.getInputParameters();
    FlightUtils.validateRequiredEntries(inputMap, ControlledResourceKeys.CREATION_PARAMETERS);
    var creationParameters =
        inputMap.get(
            ControlledResourceKeys.CREATION_PARAMETERS, ApiAzureVmCreationParameters.class);

    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    final Optional<ControlledAzureIpResource> ipResource =
        Optional.ofNullable(resource.getIpId())
            .map(
                ipId ->
                    resourceDao
                        .getResource(resource.getWorkspaceId(), ipId)
                        .castByEnum(WsmResourceType.CONTROLLED_AZURE_IP));

    final ControlledAzureDiskResource diskResource =
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getDiskId())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK);

    final ControlledAzureNetworkResource networkResource =
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getNetworkId())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_NETWORK);

    try {
      Disk existingAzureDisk =
          computeManager
              .disks()
              .getByResourceGroup(
                  azureCloudContext.getAzureResourceGroupId(), diskResource.getDiskName());

      Optional<PublicIpAddress> existingAzureIp =
          ipResource.map(
              ipRes ->
                  computeManager
                      .networkManager()
                      .publicIpAddresses()
                      .getByResourceGroup(
                          azureCloudContext.getAzureResourceGroupId(), ipRes.getIpName()));

      Network existingNetwork =
          computeManager
              .networkManager()
              .networks()
              .getByResourceGroup(
                  azureCloudContext.getAzureResourceGroupId(), networkResource.getNetworkName());

      if (!context.getWorkingMap().containsKey(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY)) {
        logger.error(
            "Azure VM creation flight couldn't be completed. "
                + "Network interface name not found. FlightId: {}",
            context.getFlightId());
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new AzureNetworkInterfaceNameNotFoundException(
                String.format(
                    "Azure network interface name not found. " + "FlightId: %s",
                    context.getFlightId())));
      }
      var networkInterface =
          computeManager
              .networkManager()
              .networkInterfaces()
              .getByResourceGroup(
                  azureCloudContext.getAzureResourceGroupId(),
                  context
                      .getWorkingMap()
                      .get(AzureVmHelper.WORKING_MAP_NETWORK_INTERFACE_KEY, String.class));

      var virtualMachineDefinition =
          buildVmConfiguration(
              computeManager,
              networkInterface,
              existingAzureDisk,
              azureCloudContext.getAzureResourceGroupId(),
              creationParameters);

      virtualMachineDefinition.create(
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
                  .setPublicIpAddress(existingAzureIp.orElse(null))
                  .setImage(AzureVmUtils.getImageData(creationParameters.getVmImage()))
                  .build()));
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (ManagementExceptionUtils.isConflict(e)) {
        logger.info(
            "Azure Vm {} in managed resource group {} already exists",
            resource.getVmName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      if (ManagementExceptionUtils.isResourceNotFound(e)) {
        logger.info(
            "Either the disk, ip, or network passed into this createVm does not exist "
                + String.format(
                    "%nResource Group: %s%n\tIp Name: %s%n\tNetwork Name: %s%n\tDisk Name: %s",
                    azureCloudContext.getAzureResourceGroupId(),
                    ipResource.isPresent() ? ipResource.get().getIpName() : "NoPublicIp",
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
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    return AzureVmHelper.deleteVm(azureCloudContext, computeManager, resource.getVmName());
  }

  private VirtualMachine.DefinitionStages.WithCreate buildVmConfiguration(
      ComputeManager computeManager,
      NetworkInterface networkInterface,
      Disk disk,
      String azureResourceGroupId,
      ApiAzureVmCreationParameters creationParameters) {
    var vmConfigurationCommonStep =
        computeManager
            .virtualMachines()
            .define(resource.getVmName())
            .withRegion(resource.getRegion())
            .withExistingResourceGroup(azureResourceGroupId)
            .withExistingPrimaryNetworkInterface(networkInterface);

    VirtualMachine.DefinitionStages.WithCreate vmConfigurationFinalStep;
    if (creationParameters.getVmImage().getUri() != null) {
      vmConfigurationFinalStep =
          vmConfigurationCommonStep
              .withSpecializedLinuxCustomImage(creationParameters.getVmImage().getUri())
              .withExistingDataDisk(disk)
              .withTag("workspaceId", resource.getWorkspaceId().toString())
              .withTag("resourceId", resource.getResourceId().toString())
              .withSize(VirtualMachineSizeTypes.fromString(resource.getVmSize()));
    } else {
      vmConfigurationFinalStep =
          vmConfigurationCommonStep
              .withSpecificLinuxImageVersion(
                  new ImageReference()
                      .withPublisher(creationParameters.getVmImage().getPublisher())
                      .withOffer(creationParameters.getVmImage().getOffer())
                      .withSku(creationParameters.getVmImage().getSku())
                      .withVersion(creationParameters.getVmImage().getVersion()))
              .withRootUsername(creationParameters.getVmUser().getName())
              .withRootPassword(creationParameters.getVmUser().getPassword())
              .withExistingDataDisk(disk)
              .withTag("workspaceId", resource.getWorkspaceId().toString())
              .withTag("resourceId", resource.getResourceId().toString())
              .withSize(VirtualMachineSizeTypes.fromString(resource.getVmSize()));
    }

    if (creationParameters.getCustomScriptExtension() != null) {
      var customScriptExtension =
          vmConfigurationFinalStep
              .defineNewExtension(creationParameters.getCustomScriptExtension().getName())
              .withPublisher(creationParameters.getCustomScriptExtension().getPublisher())
              .withType(creationParameters.getCustomScriptExtension().getType())
              .withVersion(creationParameters.getCustomScriptExtension().getVersion())
              .withPublicSettings(
                  AzureVmUtils.settingsFrom(
                      creationParameters.getCustomScriptExtension().getPublicSettings()))
              .withProtectedSettings(
                  AzureVmUtils.settingsFrom(
                      creationParameters.getCustomScriptExtension().getProtectedSettings()))
              .withTags(
                  AzureVmUtils.tagsFrom(creationParameters.getCustomScriptExtension().getTags()));

      if (creationParameters.getCustomScriptExtension().isMinorVersionAutoUpgrade()) {
        customScriptExtension.withMinorVersionAutoUpgrade();
      } else {
        customScriptExtension.withoutMinorVersionAutoUpgrade();
      }
      customScriptExtension.attach();
    }
    return vmConfigurationFinalStep;
  }
}
