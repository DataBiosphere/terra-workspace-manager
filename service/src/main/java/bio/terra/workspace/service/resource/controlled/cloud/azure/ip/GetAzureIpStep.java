package bio.terra.workspace.service.resource.controlled.cloud.azure.ip;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;

/**
 * Gets an Azure IP, and fails if it already exists. This step is designed to run immediately before
 * {@link CreateAzureIpStep} to ensure idempotency of the create operation.
 */
public class GetAzureIpStep implements Step {

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureIpResource resource;

  public GetAzureIpStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureIpResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    try {
      computeManager
          .networkManager()
          .publicIpAddresses()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getIpName());
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure IP with name %s already exists in resource group %s",
                  azureCloudContext.getAzureResourceGroupId(), resource.getIpName())));
    } catch (ManagementException e) {
      if (ManagementExceptionUtils.isResourceNotFound(e)) {
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
