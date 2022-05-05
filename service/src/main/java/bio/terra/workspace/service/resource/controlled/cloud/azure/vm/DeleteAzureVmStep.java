package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAzureVmStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureVmStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureVmResource resource;

  public DeleteAzureVmStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureVmResource resource) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachines/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            resource.getVmName());
    try {
      logger.info("Attempting to delete vm " + azureResourceId);

      VirtualMachine resolvedVm =
          computeManager
              .virtualMachines()
              .getByResourceGroup(
                  azureCloudContext.getAzureResourceGroupId(), resource.getVmName());

      computeManager.virtualMachines().deleteById(azureResourceId);

      computeManager.networkManager().networkInterfaces().deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), String.format("nic-%s", resource.getVmName()));

      // Delete the OS disk
      computeManager.disks().deleteById(resolvedVm.osDiskId());

      return StepResult.getStepResultSuccess();
    } catch (Exception ex) {
      logger.info("Attempt to delete Azure vm failed on this try: " + azureResourceId, ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure vm resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
