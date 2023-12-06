package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementException;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstallCustomVmExtension implements Step {
  private static final Logger logger = LoggerFactory.getLogger(InstallCustomVmExtension.class);

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;

  public InstallCustomVmExtension(AzureConfiguration azureConfig, CrlService crlService) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // todo factor into a helper class
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    final String vmId = context.getWorkingMap().get(AzureVmHelper.WORKING_MAP_VM_ID, String.class);
    var virtualMachine = computeManager.virtualMachines().getById(vmId);
    FlightMap inputParameters = context.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters, WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS);
    var creationParameters =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS,
            ApiAzureVmCreationParameters.class);

    var result = StepResult.getStepResultSuccess();
    if (creationParameters.getCustomScriptExtension() != null) {
      logger.info("Installing custom script extension on VM {}", vmId);
      result = installVmExtension(virtualMachine, creationParameters);
    } else {
      logger.info("No custom script extension to install on VM {}, skipping", vmId);
    }
    // TODO error handling

    return result;
  }

  private StepResult installVmExtension(
      VirtualMachine virtualMachine, ApiAzureVmCreationParameters creationParameters) {
    try {
      var customScriptExtension =
          virtualMachine
              .update()
              .defineNewExtension(creationParameters.getCustomScriptExtension().getName())
              .withPublisher(creationParameters.getCustomScriptExtension().getPublisher())
              .withType(creationParameters.getCustomScriptExtension().getType())
              .withVersion(creationParameters.getCustomScriptExtension().getVersion())
              .withPublicSettings(
                  AzureUtils.vmSettingsFrom(
                      creationParameters.getCustomScriptExtension().getPublicSettings()))
              .withProtectedSettings(
                  AzureUtils.vmSettingsFrom(
                      creationParameters.getCustomScriptExtension().getProtectedSettings()))
              .withTags(
                  AzureUtils.vmTagsFrom(creationParameters.getCustomScriptExtension().getTags()));

      if (creationParameters.getCustomScriptExtension().isMinorVersionAutoUpgrade()) {
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
        default -> new StepResult(
            AzureManagementExceptionUtils.maybeRetryStatus(e), new AzureManagementException(e));
      };
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // TODO
    return null;
  }
}
