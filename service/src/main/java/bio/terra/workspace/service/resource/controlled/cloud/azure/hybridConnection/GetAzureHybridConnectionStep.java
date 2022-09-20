package bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection;

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
import com.azure.resourcemanager.relay.RelayManager;

/**
 * Gets an Azure RelayNamespace, and fails if it already exists. This step is designed to run
 * immediately before {@link CreateAzureHybridConnectionStep} to ensure idempotency of the create
 * operation.
 */
public class GetAzureHybridConnectionStep implements Step {

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureHybridConnectionResource resource;

  public GetAzureHybridConnectionStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureHybridConnectionResource resource) {
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
    RelayManager manager = crlService.getRelayManager(azureCloudContext, azureConfig);
    try {
      manager // TODO get hcName
          .hybridConnections()
          .get(azureCloudContext.getAzureResourceGroupId(), "namespaceTODO", "hcName");
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure Hybrid Connection with name %s already exists in resource group %s",
                  azureCloudContext.getAzureResourceGroupId(), resource.getName())));
    } catch (ManagementException e) {
      if (ManagementExceptionUtils.isExceptionCode(
          e, ManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
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
