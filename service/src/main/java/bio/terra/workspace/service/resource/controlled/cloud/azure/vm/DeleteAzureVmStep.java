package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAzureVmStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureVmStep.class);
  private final AzureConfiguration azureConfig;
  private final ResourceDao resourceDao;
  private final CrlService crlService;
  private final AzureCloudContext azureCloudContext;

  private final UUID workspaceId;
  private final UUID resourceId;

  public DeleteAzureVmStep(
      AzureConfiguration azureConfig,
      AzureCloudContext azureCloudContext,
      CrlService crlService,
      ResourceDao resourceDao,
      UUID workspaceId,
      UUID resourceId) {
    this.crlService = crlService;
    this.resourceDao = resourceDao;
    this.azureCloudContext = azureCloudContext;
    this.azureConfig = azureConfig;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    var wsmResource = resourceDao.getResource(workspaceId, resourceId);
    var vm = wsmResource.castToControlledResource().castToAzureVmResource();

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachines/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            vm.getVmName());
    try {
      logger.info("Attempting to delete vm " + azureResourceId);

      VirtualMachine resolvedVm =
          computeManager
              .virtualMachines()
              .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vm.getVmName());

      computeManager.virtualMachines().deleteById(azureResourceId);

      resolvedVm
          .networkInterfaceIds()
          .forEach(nic -> computeManager.networkManager().networkInterfaces().deleteById(nic));

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
        "Cannot undo delete of Azure vm resource {} in workspace {}.", resourceId, workspaceId);
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
