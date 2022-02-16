package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets an Azure RelayNamespace, and fails if it already exists. This step is designed to run immediately before
 * {@link CreateAzureRelayNamespaceStep} to ensure idempotency of the create operation.
 */
public class GetAzureRelayNamespaceStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GetAzureRelayNamespaceStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureRelayNamespaceResource resource;

  public GetAzureRelayNamespaceStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureRelayNamespaceResource resource) {
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
      manager
          .namespaces()
          .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), resource.getNamespaceName());
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure Relay Namespace with name %s already exists in resource group %s",
                  azureCloudContext.getAzureResourceGroupId(), resource.getName())));
    } catch (ManagementException e) {
      // Azure error codes can be found here:
      // https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/common-deployment-errors
      if (StringUtils.contains(e.getValue().getCode(), "ResourceNotFound")) {
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
