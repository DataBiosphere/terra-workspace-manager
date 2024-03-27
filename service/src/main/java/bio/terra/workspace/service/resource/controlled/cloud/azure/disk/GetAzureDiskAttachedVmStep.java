package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This steps works as a companion step for a disk deletion process. It gets identifier of a virtual
 * machine to which data disk is attached and saves it into flight's working map. This data is
 * required for 'undo' operation at DetachAzureDiskFromVmStep. It is required in case a disk has
 * been detached from a virtual machine, but we need to attach it back in corresponding 'undo'
 * operation.
 */
public class GetAzureDiskAttachedVmStep implements DeleteControlledResourceStep {

  private static final Logger logger = LoggerFactory.getLogger(GetAzureDiskAttachedVmStep.class);

  private final CrlService crlService;
  private final AzureConfiguration azureConfig;
  private final ControlledAzureDiskResource resource;

  public GetAzureDiskAttachedVmStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureDiskResource resource) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        DeleteAzureDiskFlightUtils.getAzureCloudContext(context);
    final String diskResourceId =
        DeleteAzureDiskFlightUtils.getAzureDiskResourceId(azureCloudContext, resource);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    try {
      var disk = computeManager.disks().getById(diskResourceId);
      if (disk.isAttachedToVirtualMachine()) {
        context
            .getWorkingMap()
            .put(DeleteAzureDiskFlightUtils.DISK_ATTACHED_VM_ID_KEY, disk.virtualMachineId());
      }
    } catch (ManagementException ex) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          ex, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Disk '{}' does not exist in workspace {}.",
            resource.getResourceId(),
            resource.getWorkspaceId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // nothing to undo here
    return StepResult.getStepResultSuccess();
  }
}
