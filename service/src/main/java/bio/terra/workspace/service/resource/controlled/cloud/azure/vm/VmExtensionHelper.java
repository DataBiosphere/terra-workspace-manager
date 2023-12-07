package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.exception.AzureManagementException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtension;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Knows how to install and manage a custom script extension on a VM */
public class VmExtensionHelper {
  private static final Logger logger = LoggerFactory.getLogger(VmExtensionHelper.class);

  enum ExtensionStatus {
    CANCELED("Canceled"),
    CREATING("Creating"),
    FAILED("Failed"),
    SUCCEEDED("Succeeded"),
    NOT_PRESENT("NotPresent");

    private final String status;

    ExtensionStatus(String status) {
      this.status = status;
    }

    public String toString() {
      return status;
    }
  }

  public StepResult maybeInstallExtension(
      ApiAzureVmCreationParameters creationParameters,
      AzureCloudContext azureCloudContext,
      String vmId,
      ComputeManager computeManager) {
    var result = StepResult.getStepResultSuccess();
    if (Objects.nonNull(creationParameters)
        && creationParameters.getCustomScriptExtension() != null) {

      logger.info("Checking if custom script extension is already installed on VM {}", vmId);
      var extensionStatus =
          this.checkExtensionInstalled(
              vmId, creationParameters.getCustomScriptExtension(), computeManager);
      switch (extensionStatus) {
        case FAILED:
        case CANCELED:
          logger.info("Custom script extension is in a failed or canceled state, uninstalling");
          this.uninstallVmExtension(
              vmId, creationParameters.getCustomScriptExtension(), computeManager);
        case CREATING:
          // todo better exception
          var msg = "Custom script extension is still being created in VM " + vmId + ", retrying";
          logger.info(msg);
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, new RuntimeException(msg));
        case SUCCEEDED:
          logger.info("Custom script extension is already installed on VM {}, skipping", vmId);
          return result;
      }

      logger.info("Installing custom script extension on VM {}", vmId);
      result =
          this.installVmExtension(
              vmId, creationParameters.getCustomScriptExtension(), computeManager);
    } else {
      logger.info("No custom script extension to install on VM {}, skipping", vmId);
    }

    return result;
  }

  public StepResult maybeUninstallExtension(
      ApiAzureVmCreationParameters creationParams, String vmId, ComputeManager computeManager) {
    if (Objects.isNull(creationParams)
        || Objects.isNull(creationParams.getCustomScriptExtension())) {
      logger.info("No custom script extension to uninstall on VM {}, skipping", vmId);
      return StepResult.getStepResultSuccess();
    }

    var state =
        this.checkExtensionInstalled(
            vmId, creationParams.getCustomScriptExtension(), computeManager);

    switch (state) {
      case FAILED:
      case CANCELED:
      case SUCCEEDED:
        logger.info("Uninstalling custom script extension on VM {}", vmId);
        this.uninstallVmExtension(vmId, creationParams.getCustomScriptExtension(), computeManager);
        break;
      case CREATING:
        logger.info("Custom script extension is still being created in VM {}, skipping", vmId);
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_RETRY,
            new RuntimeException(
                "Custom script extension is still being created in VM " + vmId + ", retrying"));
      case NOT_PRESENT:
        logger.info("Custom script extension is not installed on VM {}, skipping", vmId);
        break;
    }

    return StepResult.getStepResultSuccess();
  }

  private ExtensionStatus checkExtensionInstalled(
      String virtualMachineId,
      ApiAzureVmCustomScriptExtension customScriptExtensionConfig,
      ComputeManager computeManager) {
    var virtualMachine = computeManager.virtualMachines().getById(virtualMachineId);
    var extensions = virtualMachine.listExtensions();
    if (extensions.containsKey(customScriptExtensionConfig.getName())) {
      logger.info(
          "Custom script extension already installed on VM {}, checking status",
          virtualMachine.id());
      var extension = extensions.get(customScriptExtensionConfig.getName());
      // https://learn.microsoft.com/en-us/rest/api/azurestack/vm-extensions/create?view=rest-azurestack-2015-12-01-preview&tabs=HTTP#provisioningstate
      var state = ExtensionStatus.valueOf(extension.provisioningState());
      return state;
    } else {
      logger.info("Custom script extension not installed on VM {}", virtualMachine.id());
    }
    return ExtensionStatus.NOT_PRESENT;
  }

  private void uninstallVmExtension(
      String virtualMachineId,
      ApiAzureVmCustomScriptExtension customScriptExtensionConfig,
      ComputeManager computeManager) {
    var virtualMachine = computeManager.virtualMachines().getById(virtualMachineId);
    var extensions = virtualMachine.listExtensions();
    if (extensions.containsKey(customScriptExtensionConfig.getName())) {
      logger.info(
          "Custom script extension already installed on VM {}, uninstalling", virtualMachine.id());
      virtualMachine.update().withoutExtension(customScriptExtensionConfig.getName()).apply();
      logger.info("Custom script extension successfully uninstalled on VM {}", virtualMachine.id());
    } else {
      logger.info("Custom script extension not installed on VM {}", virtualMachine.id());
    }
  }

  /**
   * Installs a custom script extension on a VM using the given VM information and extension
   * configuration
   *
   * @param customScriptExtensionConfig Configuration for the custom script extension
   * @return Result of the operation
   */
  private StepResult installVmExtension(
      String virtualMachineId,
      ApiAzureVmCustomScriptExtension customScriptExtensionConfig,
      ComputeManager computeManager) {
    var virtualMachine = computeManager.virtualMachines().getById(virtualMachineId);

    try {
      var customScriptExtension =
          virtualMachine
              .update()
              .defineNewExtension(customScriptExtensionConfig.getName())
              .withPublisher(customScriptExtensionConfig.getPublisher())
              .withType(customScriptExtensionConfig.getType())
              .withVersion(customScriptExtensionConfig.getVersion())
              .withPublicSettings(
                  AzureUtils.vmSettingsFrom(customScriptExtensionConfig.getPublicSettings()))
              .withProtectedSettings(
                  AzureUtils.vmSettingsFrom(customScriptExtensionConfig.getProtectedSettings()))
              .withTags(AzureUtils.vmTagsFrom(customScriptExtensionConfig.getTags()));

      if (customScriptExtensionConfig.isMinorVersionAutoUpgrade()) {
        customScriptExtension.withMinorVersionAutoUpgrade();
      } else {
        customScriptExtension.withoutMinorVersionAutoUpgrade();
      }
      customScriptExtension.attach().apply();
      logger.info("Successfully installed custom script extension on VM {}", virtualMachine.id());
    } catch (ManagementException e) {
      return switch (e.getValue().getCode()) {
        case AzureManagementExceptionUtils.VM_EXTENSION_PROVISIONING_ERROR -> {
          logger.error("Error provisioning VM extension");
          yield new StepResult(
              StepStatus.STEP_RESULT_FAILURE_FATAL, new AzureManagementException(e));
        }
        case AzureManagementExceptionUtils.CONFLICT -> {
          logger.info("Custom script extension already installed on VM {}", virtualMachine.id());
          yield StepResult.getStepResultSuccess();
        }
        default -> new StepResult(
            AzureManagementExceptionUtils.maybeRetryStatus(e), new AzureManagementException(e));
      };
    }

    return StepResult.getStepResultSuccess();
  }
}
