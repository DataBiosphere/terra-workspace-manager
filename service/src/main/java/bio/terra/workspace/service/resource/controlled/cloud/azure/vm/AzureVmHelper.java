package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identity;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AzureVmHelper {
  public static final String WORKING_MAP_NETWORK_INTERFACE_KEY = "NetworkInterfaceName";
  public static final String WORKING_MAP_SUBNET_NAME = "SubnetName";
  public static final String WORKING_MAP_VM_ID = "VmId";
  private static final int NIC_RESERVED_FOR_ANOTHER_VM_ERROR_RETRY_SECONDS = 180;
  private static final Logger logger = LoggerFactory.getLogger(AzureVmHelper.class);

  public static StepResult deleteVm(
      AzureCloudContext azureCloudContext, ComputeManager computeManager, String vmName) {
    VirtualMachine resolvedVm = null;
    try {
      resolvedVm =
          computeManager
              .virtualMachines()
              .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vmName);

      computeManager
          .virtualMachines()
          .deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vmName);
    } catch (ManagementException e) {
      handleNotFound(e, "VM", vmName, azureCloudContext.getAzureResourceGroupId());
    }

    // TODO: If VM is already deleted, nic and disk will fail to delete
    if (resolvedVm != null)
      try {
        computeManager.disks().deleteById(resolvedVm.osDiskId());
      } catch (ManagementException e) {
        handleNotFound(
            e, "disk", resolvedVm.osDiskId(), azureCloudContext.getAzureResourceGroupId());
      }

    return StepResult.getStepResultSuccess();
  }

  public static StepResult deleteNetworkInterface(
      AzureCloudContext azureCloudContext,
      ComputeManager computeManager,
      String networkInterfaceName) {
    try {
      computeManager
          .networkManager()
          .networkInterfaces()
          .deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), networkInterfaceName);
    } catch (ManagementException e) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.NIC_RESERVED_FOR_ANOTHER_VM)) {
        // In case of this particular error Azure asks to wait for 180 seconds before next retry. At
        // least at the time this code was written.
        // It would be good to have retry delay as a part of details field, so we can adjust
        // automatically. But it is just a part of the message.
        // Error message example below:
        // "error": {
        //  "code": "NicReservedForAnotherVm",
        //          "message": "Nic(s) in request is reserved for another Virtual Machine for 180
        // seconds.
        //          Please provide another nic(s) or retry after 180 seconds.
        //          Reserved VM:
        // /subscriptions/3efc5bdf-be0e-44e7-b1d7-c08931e3c16c/resourceGroups/mrg-terra-integration-test-20211118/providers/Microsoft.Compute/virtualMachines/az-vm-b606ad7d-9b00-463d-9b6e-d70586d17eb2",
        //          "details": []
        // }
        try {
          TimeUnit.SECONDS.sleep(NIC_RESERVED_FOR_ANOTHER_VM_ERROR_RETRY_SECONDS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
        }
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      } else {
        throw e;
      }
    }
    return StepResult.getStepResultSuccess();
  }

  public static StepResult removeAllUserAssignedManagedIdentitiesFromVm(
      AzureCloudContext azureCloudContext, ComputeManager computeManager, String vmName) {
    VirtualMachine virtualMachine;
    Set<String> userAssignedMsiIds;
    Exception removalException = null;
    // Resolve VM name.
    try {
      virtualMachine =
          computeManager
              .virtualMachines()
              .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vmName);
    } catch (ManagementException e) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        // TODO: if vm gets deleted before this process finishes, the Identity assignment still
        // needs to be updated to remove access to vm.
        // This is going to be a more tedious process involving traversing through all identities in
        // managed resource group.
        logger.info(
            "Azure VM {} in managed resource group {} is not found.",
            vmName,
            azureCloudContext.getAzureResourceGroupId());
        // Returning success for now.
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    try {
      userAssignedMsiIds = virtualMachine.userAssignedManagedServiceIdentityIds();
    } catch (ManagementException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    if (userAssignedMsiIds == null || userAssignedMsiIds.isEmpty()) {
      logger.info(
          "Azure VM {} in managed resource group {} has no user-assigned managed identities assigned",
          vmName,
          azureCloudContext.getAzureResourceGroupId());
      return StepResult.getStepResultSuccess();
    }

    for (String userAssignedMsiId : userAssignedMsiIds) {
      try {
        virtualMachine
            .update()
            .withoutUserAssignedManagedServiceIdentity(userAssignedMsiId)
            .apply();
      } catch (ManagementException e) {
        removalException = e;
        logger.info(
            "Error removing managed identity {} from Azure VM {} in managed resource group {}.",
            userAssignedMsiId,
            vmName,
            azureCloudContext.getAzureResourceGroupId());
      }
    }

    if (removalException != null) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, removalException);
    }
    return StepResult.getStepResultSuccess();
  }

  public static StepResult assignManagedIdentityToVm(
      AzureCloudContext azureCloudContext,
      ComputeManager computeManager,
      MsiManager msiManager,
      String vmName,
      String petManagedIdentityId) {
    Identity managedIdentity;
    VirtualMachine virtualMachine;

    // Resolve VM name.
    try {
      virtualMachine =
          computeManager
              .virtualMachines()
              .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vmName);
    } catch (ManagementException e) {
      logger.info(
          "Error retrieving Azure VM {} in managed resource group {}.",
          vmName,
          azureCloudContext.getAzureResourceGroupId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    // Check if identity is already assigned to VM.
    try {
      Set<String> userAssignedMsiIds = virtualMachine.userAssignedManagedServiceIdentityIds();
      if (userAssignedMsiIds != null && userAssignedMsiIds.contains(petManagedIdentityId)) {
        logger.info(
            "Azure VM {} in managed resource group {} is already assigned to user-assigned managed identity {}.",
            vmName,
            azureCloudContext.getAzureResourceGroupId(),
            petManagedIdentityId);
        return StepResult.getStepResultSuccess();
      }
    } catch (ManagementException e) {
      // We can ignore error retrieving managed identities from VM at this time,
      // And continue to assigning identity to VM.
      logger.info(
          "Error retrieving managed identities assigned to Azure VM {} in managed resource group {}.",
          vmName,
          azureCloudContext.getAzureResourceGroupId());
    }

    // Retrieve identity object by the ID.
    try {
      managedIdentity = msiManager.identities().getById(petManagedIdentityId);
    } catch (ManagementException e) {
      // TODO: add logic to handle Identity Not Found case - there is no reason to retry in this
      // case.
      logger.info(
          "Error retrieving Azure managed identity {} in managed resource group {}.",
          petManagedIdentityId,
          azureCloudContext.getAzureResourceGroupId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    // Assign managed identity to VM.
    try {
      virtualMachine
          .update()
          .withExistingUserAssignedManagedServiceIdentity(managedIdentity)
          .apply();
    } catch (ManagementException e) {
      logger.info(
          "Error assigning managed identity {} to Azure VM {} in managed resource group {}.",
          petManagedIdentityId,
          vmName,
          azureCloudContext.getAzureResourceGroupId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private static StepResult handleNotFound(
      ManagementException e, String resourceType, String resourceName, String resourceId) {
    // Stairway steps may run multiple times, so we may already have deleted this resource.
    if (AzureManagementExceptionUtils.isExceptionCode(
        e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
      logger.info(
          "Azure {} {} in managed resource group {} already deleted",
          resourceType,
          resourceName,
          resourceId);
      return StepResult.getStepResultSuccess();
    }
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
  }
}
