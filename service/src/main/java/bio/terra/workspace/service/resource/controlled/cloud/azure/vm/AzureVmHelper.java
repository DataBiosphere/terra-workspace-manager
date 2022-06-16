package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identity;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AzureVmHelper {
  private static final Logger logger = LoggerFactory.getLogger(AzureVmHelper.class);

  public static StepResult deleteVm(
      AzureCloudContext azureCloudContext, ComputeManager computeManager, String vmName)
      throws InterruptedException {
    var nicName = String.format("nic-%s", vmName);
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
      handleNotFound(e, vmName, azureCloudContext.getAzureResourceGroupId());
    }

    try {
      computeManager
          .networkManager()
          .networkInterfaces()
          .deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), nicName);
    } catch (ManagementException e) {
      handleNotFound(e, vmName, azureCloudContext.getAzureResourceGroupId());
    }

    // TODO: If VM is already deleted, nic and disk will fail to delete
    if (resolvedVm != null)
      try {
        computeManager.disks().deleteById(resolvedVm.osDiskId());
      } catch (ManagementException e) {
        handleNotFound(e, vmName, azureCloudContext.getAzureResourceGroupId());
      }

    return StepResult.getStepResultSuccess();
  }

  public static StepResult removeAllUserAssignedManagedIdentitiesFromVm(
      AzureCloudContext azureCloudContext, ComputeManager computeManager, String vmName) {
    try {
      final VirtualMachine virtualMachine =
          computeManager
              .virtualMachines()
              .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vmName);

      Set<String> userAssignedMsiIds = virtualMachine.userAssignedManagedServiceIdentityIds();

      if (userAssignedMsiIds == null || userAssignedMsiIds.isEmpty()) {
        logger.info(
            "Azure VM {} in managed resource group {} has no user-assigned managed identities assigned",
            vmName,
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }

      for (String userAssignedMsiId : userAssignedMsiIds) {
        virtualMachine.update().withoutUserAssignedManagedServiceIdentity(userAssignedMsiId);
      }
      ;
    } catch (ManagementException e) {
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
        // TODO: if vm gets deleted before this process finishes, the Identity assignment still
        // needs to be updated to remove access to vm.
        // This is going to be a more tedious process involving traversing through all identities in
        // managed resource group.
        logger.info(
            "Azure VM {} in managed resource group {} has already been deleted",
            vmName,
            azureCloudContext.getAzureResourceGroupId());
        // Returning success for now.
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  public static StepResult assignPetManagedIdentityToVm(
      AzureCloudContext azureCloudContext,
      ComputeManager computeManager,
      MsiManager msiManager,
      String vmName,
      String petManagedIdentityId) {
    Identity managedIdentity;

    try {
      managedIdentity = msiManager.identities().getById(petManagedIdentityId);
    } catch (ManagementException e) {
      logger.info(
          "Error getting Azure managed identity {} in managed resource group {}.",
          petManagedIdentityId,
          azureCloudContext.getAzureResourceGroupId());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    try {
      computeManager
          .virtualMachines()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vmName)
          .update()
          .withExistingUserAssignedManagedServiceIdentity(managedIdentity);
    } catch (ManagementException e) {
      if (StringUtils.equals(e.getValue().getCode(), "Conflict")) {
        logger.info(
            "Azure VM {} in managed resource group {} does not exist.",
            vmName,
            azureCloudContext.getAzureResourceGroupId());
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private static StepResult handleNotFound(
      ManagementException e, String vmName, String resourceId) {
    // Stairway steps may run multiple times, so we may already have deleted this resource.
    if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
      logger.info("Azure VM {} in managed resource group {} already deleted", vmName, resourceId);
      return StepResult.getStepResultSuccess();
    }
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
  }
}
