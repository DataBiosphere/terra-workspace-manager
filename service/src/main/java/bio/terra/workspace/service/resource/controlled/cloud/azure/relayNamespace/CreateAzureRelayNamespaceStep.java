package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.relay.data.CreateRelayRequestData;
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
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an Azure RelayNamespace address. Designed to run directly after {@link
 * GetAzureRelayNamespaceStep}.
 */
public class CreateAzureRelayNamespaceStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateAzureRelayNamespaceStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureRelayNamespaceResource resource;

  public CreateAzureRelayNamespaceStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureRelayNamespaceResource resource) {
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
          .define(resource.getNamespaceName())
          .withRegion(resource.getRegion())
          .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
          .create(
              Defaults.buildContext(
                  CreateRelayRequestData.builder()
                      .setName(resource.getNamespaceName())
                      .setRegion(Region.fromName(resource.getRegion()))
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setTenantId(azureCloudContext.getAzureTenantId())
                      .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                      .build()));
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (ManagementExceptionUtils.isConflict(e)) {
        logger.info(
            "Azure Relay Namepace {} in managed resource group {} already exists",
            resource.getNamespaceName(),
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
      manager
          .namespaces()
          .deleteByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getNamespaceName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (ManagementExceptionUtils.isResourceNotFound(e)) {
        logger.info(
            "Azure Relay Namespace {} in managed resource group {} already deleted",
            resource.getNamespaceName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }
}
