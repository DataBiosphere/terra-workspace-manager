package bio.terra.workspace.service.resource.controlled.cloud.azure.relayHybridConnection;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure RelayHybridConnection resource. This step uses the
 * following process to actually delete the Azure RelayHybridConnection
 */
public class DeleteAzureRelayHybridConnectionStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateAzureRelayHybridConnectionStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureRelayHybridConnectionResource resource;

  public DeleteAzureRelayHybridConnectionStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureRelayHybridConnectionResource resource) {
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
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Relay/namespaces/%s/hybridConnections/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            resource.getNamespaceName(),
            resource.getHybridConnectionName());

    try {
      logger.info("Attempting to delete Relay Hybrid Connection " + azureResourceId);
      manager.hybridConnections().deleteById(azureResourceId);
      return StepResult.getStepResultSuccess();
    } catch(ManagementException e) {
      if (StringUtils.contains(e.getValue().getCode(), "NotFound")) {
        logger.info(
                "Azure Relay Hybrid Connection "+ azureResourceId + " already deleted",
                e);
        return StepResult.getStepResultSuccess();
      }
      logger.info(
              "Attempt to delete Azure Relay Hybrid Connection failed on this try: " + azureResourceId,
              e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (Exception ex) {
      logger.info(
          "Attempt to delete Azure Relay Hybrid Connection failed on this try: " + azureResourceId,
          ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure Relay Hybrid Connection resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
