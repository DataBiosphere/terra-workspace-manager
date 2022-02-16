package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.relay.RelayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure RelayNamespace resource. This step uses the following process to
 * actually delete the Azure RelayNamespace
 */
public class DeleteAzureRelayNamespaceStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureRelayNamespaceStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureRelayNamespaceResource resource;

  public DeleteAzureRelayNamespaceStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureRelayNamespaceResource resource) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    RelayManager manager = crlService.getRelayManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Relay/namespaces/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            resource.getNamespaceName());

    try {
      logger.info("Attempting to delete Relay Namespace " + azureResourceId);
      manager.namespaces().deleteById(azureResourceId);
      return StepResult.getStepResultSuccess();
    } catch (Exception ex) {
      logger.info("Attempt to delete Azure Relay Namespace failed on this try: " + azureResourceId, ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure Relay Namespace resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
