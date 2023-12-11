package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_RESOURCE_REGION;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.compute.data.CreateVirtualMachineRequestData;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.exception.AzureNetworkInterfaceNameNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.DiffDiskPlacement;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.ImageReference;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.azure.resourcemanager.network.models.NetworkInterface;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    FlightMap inputParameters = context.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters, ControlledResourceKeys.CREATION_PARAMETERS);
    var creationParameters =
        inputParameters.get(
            ControlledResourceKeys.CREATION_PARAMETERS, ApiAzureVmCreationParameters.class);

    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    final Optional<ControlledAzureDiskResource> diskResource =
        Optional.ofNullable(resource.getDiskId())
            .map(
                diskId ->
                    resourceDao
                        .getResource(resource.getWorkspaceId(), diskId)
                        .castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK));

    final String subnetName =
        context.getWorkingMap().get(AzureVmHelper.WORKING_MAP_SUBNET_NAME, String.class);

    try {
      Optional<Disk> existingAzureDisk =
          diskResource.map(
              diskRes ->
                  computeManager
                      .disks()
                      .getByResourceGroup(
                          azureCloudContext.getAzureResourceGroupId(), diskRes.getDiskName()));

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

      // TODO:
      // The VM region is now determined by the network instead of the request.
      // As we can't have a VM and a NIC in different regions.
      // This also means we are ignoring the region parameter, and we will have
      // to remove it. Please note that keeping the region as option, requires
      // validation that the user provided region is the same as network region,
      // therefore rendering the flexibility of the option moot.
      var region =
          Region.fromName(context.getWorkingMap().get(CREATE_RESOURCE_REGION, String.class));

      var virtualMachineDefinition =
          buildVmConfiguration(
              computeManager,
              networkInterface,
              existingAzureDisk,
              azureCloudContext.getAzureResourceGroupId(),
              creationParameters,
              region);

      var createdVm =
          virtualMachineDefinition.create(
              Defaults.buildContext(
                  CreateVirtualMachineRequestData.builder()
                      .setName(resource.getVmName())
                      .setRegion(region)
                      .setTenantId(azureCloudContext.getAzureTenantId())
                      .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setNetwork(networkInterface.primaryIPConfiguration().getNetwork())
                      .setSubnetName(subnetName)
                      .setDisk(existingAzureDisk.orElse(null))
                      .setImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
                      .build()));

      context.getWorkingMap().put(AzureVmHelper.WORKING_MAP_VM_ID, createdVm.id());

    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      return switch (e.getValue().getCode()) {
        case AzureManagementExceptionUtils.CONFLICT -> {
          logger.info(
              "Azure Vm {} in managed resource group {} already exists",
              resource.getVmName(),
              azureCloudContext.getAzureResourceGroupId());
          yield StepResult.getStepResultSuccess();
        }

        case AzureManagementExceptionUtils.RESOURCE_NOT_FOUND -> {
          logger.error(
              "Either the disk, ip, or network passed into this createVm does not exist "
                  + String.format(
                      "%nResource Group: %s%n\tDisk Name: %s",
                      azureCloudContext.getAzureResourceGroupId(),
                      diskResource
                          .map(ControlledAzureDiskResource::getDiskName)
                          .orElse("<no disk>")));
          yield new StepResult(
              StepStatus.STEP_RESULT_FAILURE_FATAL, new AzureManagementException(e));
        }

        default -> new StepResult(
            AzureManagementExceptionUtils.maybeRetryStatus(e), new AzureManagementException(e));
      };
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
      Optional<Disk> disk,
      String azureResourceGroupId,
      ApiAzureVmCreationParameters creationParameters,
      Region region) {
    var vmConfigurationCommonStep =
        computeManager
            .virtualMachines()
            .define(resource.getVmName())
            .withRegion(region)
            .withExistingResourceGroup(azureResourceGroupId)
            .withExistingPrimaryNetworkInterface(networkInterface);

    var withImage = addImageStep(vmConfigurationCommonStep, creationParameters);
    var withCustomData = maybeAddCustomDataStep(withImage, creationParameters);
    var withExistingDisk = maybeAddExistingDiskStep(withCustomData, disk);
    var withEphemeralDisk = maybeAddEphemeralDiskStep(withExistingDisk, creationParameters);

    var vmConfigurationFinalStep =
        withEphemeralDisk
            .withTag("workspaceId", resource.getWorkspaceId().toString())
            .withTag("resourceId", resource.getResourceId().toString())
            .withSize(VirtualMachineSizeTypes.fromString(resource.getVmSize()));

    return vmConfigurationFinalStep;
  }

  private VirtualMachine.DefinitionStages.WithManagedCreate maybeAddEphemeralDiskStep(
      VirtualMachine.DefinitionStages.WithManagedCreate priorSteps,
      ApiAzureVmCreationParameters creationParameters) {
    var maybePlacement =
        Optional.ofNullable(creationParameters.getEphemeralOSDisk())
            .flatMap(
                ephemeralOSDisk ->
                    switch (ephemeralOSDisk) {
                      case OS_CACHE -> Optional.of(DiffDiskPlacement.CACHE_DISK);
                      case TMP_DISK -> Optional.of(DiffDiskPlacement.RESOURCE_DISK);
                      default -> Optional.empty();
                    });

    return maybePlacement
        .map(diskPlacement -> priorSteps.withEphemeralOSDisk().withPlacement(diskPlacement))
        .orElse(priorSteps);
  }

  private VirtualMachine.DefinitionStages.WithManagedCreate maybeAddExistingDiskStep(
      VirtualMachine.DefinitionStages.WithFromImageCreateOptionsManaged priorSteps,
      Optional<Disk> disk) {
    return disk.map(priorSteps::withExistingDataDisk).orElse(priorSteps);
  }

  private VirtualMachine.DefinitionStages.WithFromImageCreateOptionsManaged maybeAddCustomDataStep(
      VirtualMachine.DefinitionStages.WithFromImageCreateOptionsManaged priorSteps,
      ApiAzureVmCreationParameters creationParameters) {
    return StringUtils.isEmpty(creationParameters.getCustomData())
        ? priorSteps
        : priorSteps.withCustomData(creationParameters.getCustomData());
  }

  private VirtualMachine.DefinitionStages.WithFromImageCreateOptionsManaged addImageStep(
      VirtualMachine.DefinitionStages.WithProximityPlacementGroup priorSteps,
      ApiAzureVmCreationParameters creationParameters) {
    if (creationParameters.getVmImage().getUri() != null) {
      return priorSteps.withSpecializedLinuxCustomImage(creationParameters.getVmImage().getUri());
    } else {
      return priorSteps
          .withSpecificLinuxImageVersion(
              new ImageReference()
                  .withPublisher(creationParameters.getVmImage().getPublisher())
                  .withOffer(creationParameters.getVmImage().getOffer())
                  .withSku(creationParameters.getVmImage().getSku())
                  .withVersion(creationParameters.getVmImage().getVersion()))
          .withRootUsername(creationParameters.getVmUser().getName())
          .withRootPassword(creationParameters.getVmUser().getPassword());
    }
  }
}
