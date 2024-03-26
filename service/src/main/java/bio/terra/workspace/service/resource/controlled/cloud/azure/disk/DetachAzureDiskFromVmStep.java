package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This steps works as a companion step for a disk deletion process. It provides an additional
 * functionality of detaching data disk from a virtual machine before disk deletion. It is not
 * possible to delete a disk without detaching it.
 */
public class DetachAzureDiskFromVmStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger = LoggerFactory.getLogger(DetachAzureDiskFromVmStep.class);

  private final CrlService crlService;
  private final AzureConfiguration azureConfig;
  private final ControlledAzureDiskResource resource;

  public DetachAzureDiskFromVmStep(
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
      if (!disk.isAttachedToVirtualMachine()) {
        logger.info(
            "Disk {} in workspace {} is not attached to vm.",
            resource.getResourceId(),
            resource.getWorkspaceId());
        return StepResult.getStepResultSuccess();
      }
      var vm = getVirtualMachine(computeManager, disk.virtualMachineId());
      if (vm.isEmpty()) {
        // this is unlikely event - disk is attached, but the vm cannot be found; let's retry
        logger.warn(
            "Disk {} is attached to vm in workspace {}, but vm cannot be found. Retrying.",
            resource.getResourceId(),
            resource.getWorkspaceId());
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
      var attachedDisk =
          vm.get().dataDisks().entrySet().stream()
              .filter(entry -> entry.getValue().name().equals(resource.getDiskName()))
              .findFirst();
      if (attachedDisk.isPresent()) {
        detachDisk(vm.get(), attachedDisk.get().getKey());
      } else {
        // something strange has happened; let's retry
        logger.warn(
            "Disk {} is attached, but not found in the list of attached disk for vm {}.",
            resource.getResourceId(),
            disk.virtualMachineId());
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
    } catch (ManagementException ex) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          ex, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Disk '{}' does not exist in workspace {}.",
            resource.getResourceId(),
            resource.getWorkspaceId());
        return StepResult.getStepResultSuccess();
      } // TODO: should we handle this exception??
      if (AzureManagementExceptionUtils.isExceptionCode(
          ex, AzureManagementExceptionUtils.OPERATION_NOT_ALLOWED)) {
        logger.error(
            "Failed attempt to detach disk {} from vm in workspace {}.",
            resource.getResourceId(),
            resource.getWorkspaceId());
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, ex);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  protected StepResult deleteResource(FlightContext context) throws InterruptedException {
    /*
    This step follows the contract for a step which deletes an Azure resource, but the implementation diverges from "standard".
    The step implements additional functionality to detach data disk from a virtual machine. It doesn't actually
    delete any azure resource. This implementation is just to follow the existing contract.
    The main logic is in the doStep method and this method won't be invoked until overriden version of doStep exists.
     */
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        DeleteAzureDiskFlightUtils.getAzureCloudContext(context);
    final String diskResourceId =
        DeleteAzureDiskFlightUtils.getAzureDiskResourceId(azureCloudContext, resource);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    try {
      var disk = computeManager.disks().getById(diskResourceId);
      var vmId =
          Optional.ofNullable(
              context
                  .getWorkingMap()
                  .get(DeleteAzureDiskFlightUtils.DISK_ATTACHED_VM_ID_KEY, String.class));
      if (vmId.isEmpty()) {
        // noop in case disk wasn't initially attached to a virtual machine
        return StepResult.getStepResultSuccess();
      }
      var vm = getVirtualMachine(computeManager, vmId.get());
      vm.ifPresent(virtualMachine -> attachDisk(virtualMachine, disk));
    } catch (ManagementException ex) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          ex, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Disk '{}' does not exist in workspace {}.",
            resource.getResourceId(),
            resource.getWorkspaceId());
        return StepResult.getStepResultSuccess();
      }
      if (AzureManagementExceptionUtils.isExceptionCode(
          ex, AzureManagementExceptionUtils.OPERATION_NOT_ALLOWED)) {
        logger.error(
            "Failed attempt to detach disk {} from vm in workspace {}.",
            resource.getResourceId(),
            resource.getWorkspaceId());
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, ex);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
    return StepResult.getStepResultSuccess();
  }

  private Optional<VirtualMachine> getVirtualMachine(ComputeManager computeManager, String vmId) {
    VirtualMachine vm;
    try {
      vm = computeManager.virtualMachines().getById(vmId);
    } catch (ManagementException ex) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          ex, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Vm to which disk was attached does not exist in workspace {}.",
            resource.getWorkspaceId());
        return Optional.empty();
      }
      throw ex;
    }
    return Optional.of(vm);
  }

  private void detachDisk(VirtualMachine vm, Integer lun) {
    var vmUpdate = vm.update();
    vmUpdate.withoutDataDisk(lun);
    vmUpdate.apply();
    logger.info(
        "Disk {} in workspace {} has been detached from vm.",
        resource.getResourceId(),
        resource.getWorkspaceId());
  }

  private void attachDisk(VirtualMachine vm, Disk disk) {
    var vmUpdate = vm.update();
    vmUpdate.withExistingDataDisk(disk);
    vmUpdate.apply();
    logger.info(
        "Disk {} in workspace {} has been attached back to a vm.",
        resource.getResourceId(),
        resource.getWorkspaceId());
  }
}
