package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAzureNetworkInterfaceStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateAzureNetworkInterfaceStep.class);

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  // Network interface is not a freestanding WSM resource. It is tightly coupled to the Vm.
  private final ControlledAzureVmResource resource;
  private final String networkInterfaceName;

  public DeleteAzureNetworkInterfaceStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureVmResource resource) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
    this.networkInterfaceName = String.format("nic-%s", resource.getVmName());
  }

  @Override
  public StepResult deleteResource(FlightContext context) {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);

    return AzureVmHelper.deleteNetworkInterface(
        azureCloudContext, computeManager, networkInterfaceName);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure network interface resource {} in workspace {}.",
        networkInterfaceName,
        resource.getWorkspaceId());
    return context.getResult();
  }
}
