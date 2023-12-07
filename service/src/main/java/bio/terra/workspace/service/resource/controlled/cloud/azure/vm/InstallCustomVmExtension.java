package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;

/**
 * Installs a custom vm extension script on a previously setup Azure VM
 *
 * <p>Input parameter requirements:
 *
 * <ol>
 *   <li>CREATION_PARAMETERS: the creation parameters for the VM
 * </ol>
 *
 * <p>Working map requirements:
 *
 * <ol>
 *   <li>WORKING_MAP_VM_ID: the VM ID
 *   <li>AZURE_CLOUD_CONTEXT: the azure cloud context holding the VM
 * </ol>
 */
public class InstallCustomVmExtension implements Step {
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final VmExtensionHelper vmExtensionHelper;

  public InstallCustomVmExtension(
      AzureConfiguration azureConfig, CrlService crlService, VmExtensionHelper vmExtensionHelper) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.vmExtensionHelper = vmExtensionHelper;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputParameters = context.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters, WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS);
    FlightUtils.validateRequiredEntries(
        context.getWorkingMap(), WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT);
    FlightUtils.validateRequiredEntries(context.getWorkingMap(), AzureVmHelper.WORKING_MAP_VM_ID);

    var azureCloudContext = getCloudContextFromFlightContext(context);
    var creationParameters = getCreationParametersFromFlightContext(context);
    var vmId = getVmIdFromFlightContext(context);

    return vmExtensionHelper.maybeInstallExtension(
        creationParameters,
        azureCloudContext,
        vmId,
        crlService.getComputeManager(azureCloudContext, azureConfig));
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var vmId = getVmIdFromFlightContext(context);
    var creationParams = getCreationParametersFromFlightContext(context);
    var cloudContext = getCloudContextFromFlightContext(context);

    return vmExtensionHelper.maybeUninstallExtension(
        creationParams, vmId, crlService.getComputeManager(cloudContext, azureConfig));
  }

  private String getVmIdFromFlightContext(FlightContext context) {
    return context.getWorkingMap().get(AzureVmHelper.WORKING_MAP_VM_ID, String.class);
  }

  private ApiAzureVmCreationParameters getCreationParametersFromFlightContext(
      FlightContext context) {
    return context
        .getInputParameters()
        .get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS,
            ApiAzureVmCreationParameters.class);
  }

  private AzureCloudContext getCloudContextFromFlightContext(FlightContext context) {

    return context
        .getWorkingMap()
        .get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class);
  }
}
