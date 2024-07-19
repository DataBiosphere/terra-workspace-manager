package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This steps works as a companion step for a disk deletion process. It gets identifier of a virtual
 * machine to which data disk is attached and saves it into flight's working map. It is required in
 * case a disk has been detached from a virtual machine, but we need to attach it back in
 * corresponding 'undo' operation. This is a separate step because the data is required for the
 * 'undo' operation at DetachAzureDiskFromVmStep, and we need to guarantee its persistence - which
 * we can't guarantee if it is from the 'do' portion of the same step.
 */
public class GetAzureDiskAttachedVmStep extends DeleteAzureControlledResourceStep {

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
  public StepResult deleteResource(FlightContext context)
      throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        DeleteAzureDiskFlightUtils.getAzureCloudContext(context);
    final String diskResourceId =
        DeleteAzureDiskFlightUtils.getAzureDiskResourceId(azureCloudContext, resource);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    var disk = computeManager.disks().getById(diskResourceId);
    if (disk.isAttachedToVirtualMachine()) {
      context
          .getWorkingMap()
          .put(DeleteAzureDiskFlightUtils.DISK_ATTACHED_VM_ID_KEY, disk.virtualMachineId());
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  protected StepResult handleResourceDeleteException(Exception e, FlightContext context) {
    if (e instanceof ManagementException ex
        && AzureManagementExceptionUtils.isExceptionCode(
            ex, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
      logger.info(
          "Disk '{}' does not exist in workspace {}.",
          resource.getResourceId(),
          resource.getWorkspaceId());
      return StepResult.getStepResultSuccess();
    }
    return super.handleResourceDeleteException(e, context);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // nothing to undo here
    return StepResult.getStepResultSuccess();
  }
}
