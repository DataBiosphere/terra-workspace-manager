package bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection;

import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.relay.fluent.models.AuthorizationRuleInner;
import com.azure.resourcemanager.relay.models.AccessRights;
import com.azure.resourcemanager.relay.models.HybridConnection;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an Azure RelayNamespace address. Designed to run directly after {@link
 * bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection.GetAzureHybridConnectionStep}.
 */
public class CreateAzureHybridConnectionStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(CreateAzureHybridConnectionStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final ControlledAzureHybridConnectionResource resource;

  public CreateAzureHybridConnectionStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      LandingZoneApiDispatch landingZoneApiDispatch,
      ControlledAzureHybridConnectionResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
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
      String relayNamespaceName = getAzureRelayNamespaceName(azureCloudContext);

      HybridConnection hybridConnection =
          manager
              .hybridConnections()
              .define(resource.getHybridConnectionName())
              .withExistingNamespace(
                  azureCloudContext.getAzureResourceGroupId(), relayNamespaceName)
              .create();

      String authRuleName = "listener";
      manager
          .hybridConnections()
          .createOrUpdateAuthorizationRule(
              azureCloudContext.getAzureResourceGroupId(),
              relayNamespaceName,
              hybridConnection.name(),
              authRuleName,
              new AuthorizationRuleInner().withRights(Arrays.asList(AccessRights.LISTEN)));

      String primaryPolicyKey =
          manager
              .hybridConnections()
              .listKeys(
                  azureCloudContext.getAzureResourceGroupId(),
                  relayNamespaceName,
                  hybridConnection.name(),
                  authRuleName)
              .primaryKey();

      // TODO return both URIs and hybridConnection name
      String hybridConnectionWebsocketUrl =
          String.format(
              "wss://%s.servicebus.windows.net/$hc/%s",
              relayNamespaceName, hybridConnection.name());
      String relayListenerConfigurationString =
          String.format(
              "Endpoint=sb://%s.servicebus.windows.net/;SharedAccessKeyName=%s;SharedAccessKey=%s;EntityPath=%s",
              relayNamespaceName, authRuleName, primaryPolicyKey, hybridConnection.name());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource. In all
      // other cases, surface the exception and attempt to retry.
      if (ManagementExceptionUtils.isExceptionCode(e, ManagementExceptionUtils.CONFLICT)) {
        logger.info(
            "Azure Hybrid Connection {} in managed resource group {} already exists",
            resource.getHybridConnectionName(), // TODO fix log
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
              azureCloudContext.getAzureResourceGroupId(),
              getAzureRelayNamespaceName(azureCloudContext),
              resource.getHybridConnectionName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (ManagementExceptionUtils.isExceptionCode(
          e, ManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Azure Hybrid Connection {} in managed resource group {} already deleted",
            resource.getHybridConnectionName(), // TODO get name
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  // TODO this logic for getting azure relay is duplicated across multiple steps
  private String getAzureRelayNamespaceName(AzureCloudContext azureCloudContext) {
    String landingZoneId = landingZoneApiDispatch.getLandingZoneId(azureCloudContext);
    Optional<ApiAzureLandingZoneDeployedResource> azureRelayResource =
        landingZoneApiDispatch.listAzureLandingZoneResources(landingZoneId).getResources().stream()
            .filter(
                purposeGroup ->
                    purposeGroup.getPurpose().equals(ResourcePurpose.SHARED_RESOURCE.toString()))
            .findFirst()
            .flatMap(
                purposeGroup ->
                    purposeGroup.getDeployedResources().stream()
                        .filter(
                            deployedResource ->
                                deployedResource
                                    .getResourceType()
                                    .equals("Microsoft.Relay/Namespaces"))
                        .findFirst());

    return azureRelayResource.get().getResourceName();
  }
}
