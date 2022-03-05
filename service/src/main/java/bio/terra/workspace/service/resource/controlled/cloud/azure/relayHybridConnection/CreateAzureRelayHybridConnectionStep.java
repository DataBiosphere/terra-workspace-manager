package bio.terra.workspace.service.resource.controlled.cloud.azure.relayHybridConnection;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.relay.data.CreateRelayHybridConnectionRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an Azure RelayHybridConnection address. Designed to run directly after {@link
 * GetAzureRelayHybridConnectionStep}.
 */
public class CreateAzureRelayHybridConnectionStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateAzureRelayHybridConnectionStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureRelayHybridConnectionResource resource;

  public CreateAzureRelayHybridConnectionStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureRelayHybridConnectionResource resource) {
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
              .withExistingNamespace(azureCloudContext.getAzureResourceGroupId(), resource.getNamespaceName())
              .withRequiresClientAuthorization(resource.isRequiresClientAuthorization())
          .create(
              Defaults.buildContext(
                  CreateRelayHybridConnectionRequestData.builder()
                      .setName(resource.getHybridConnectionName())
                      .setRegion(Region.US_CENTRAL) //TODO: bump CRL version with https://github.com/DataBiosphere/terra-cloud-resource-lib/pull/117
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setTenantId(azureCloudContext.getAzureTenantId())
                      .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                      .build()));
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      // Azure error codes can be found here:
      // https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/common-deployment-errors
      if (StringUtils.equals(e.getValue().getCode(), "Conflict")) {
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
          .hybridConnections()
          .delete(
              azureCloudContext.getAzureResourceGroupId(), resource.getNamespaceName(), resource.getHybridConnectionName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (StringUtils.equals(e.getValue().getCode(), "ResourceNotFound")) {
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
