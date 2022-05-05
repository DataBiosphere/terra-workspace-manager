package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AzureVmHelper {
    private static final Logger logger = LoggerFactory.getLogger(AzureVmHelper.class);

    public static StepResult deleteVm(AzureCloudContext azureCloudContext, ComputeManager computeManager, String vmName) throws InterruptedException {
        var nicName = String.format("nic-%s", vmName);
        try {
            VirtualMachine resolvedVm =
                    computeManager
                            .virtualMachines()
                            .getByResourceGroup(
                                    azureCloudContext.getAzureResourceGroupId(), vmName);

            //TODO: If VM is already deleted, nic and disk will fail to delete
            computeManager
                    .virtualMachines()
                    .deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), vmName);

            computeManager.networkManager().networkInterfaces().deleteByResourceGroup(azureCloudContext.getAzureResourceGroupId(), nicName);

            // Delete the OS disk
            computeManager.disks().deleteById(resolvedVm.osDiskId());
        } catch (ManagementException e) {
            // Stairway steps may run multiple times, so we may already have deleted this resource.
            if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
                logger.info(
                        "Azure VM {} in managed resource group {} already deleted",
                        vmName,
                        azureCloudContext.getAzureResourceGroupId());
                return StepResult.getStepResultSuccess();
            }
            return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
        }
        return StepResult.getStepResultSuccess();
    }
}
