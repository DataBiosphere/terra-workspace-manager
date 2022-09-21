package bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an Azure RelayNamespace address. Designed to run directly after {@link
 * bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.GetAzureHybridConnectionStep}.
 */
public class CreateAzureHybridConnectionStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(CreateAzureHybridConnectionStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureHybridConnectionResource resource;

  public CreateAzureHybridConnectionStep(
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
      manager
          .hybridConnections()
          .define(resource.getHybridConnectionName())
          .withExistingNamespace(
              azureCloudContext.getAzureResourceGroupId(), resource.getNamespaceName())
          .create();
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (ManagementExceptionUtils.isExceptionCode(e, ManagementExceptionUtils.CONFLICT)) {
        logger.info(
            "Azure Hybrid Connection {} in managed resource group {} already exists",
            resource.getRegion(), // TODO fix log
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    RelayManager manager = crlService.getRelayManager(azureCloudContext, azureConfig);

    try {
      manager.hybridConnections().deleteById("TODO get the id"); // TODO get id
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (ManagementExceptionUtils.isExceptionCode(
          e, ManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Azure Hybrid Connection {} in managed resource group {} already deleted",
            resource.getRegion(), // TODO get name
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
