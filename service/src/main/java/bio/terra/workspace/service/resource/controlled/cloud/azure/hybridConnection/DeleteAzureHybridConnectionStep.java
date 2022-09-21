package bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection;

import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.relay.RelayManager;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure RelayNamespace resource. This step uses the following
 * process to actually delete the Azure RelayNamespace
 */
public class DeleteAzureHybridConnectionStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteAzureHybridConnectionStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final ControlledAzureHybridConnectionResource resource;

  public DeleteAzureHybridConnectionStep(
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
  public StepResult doStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    RelayManager manager = crlService.getRelayManager(azureCloudContext, azureConfig);
    var azureResourceId = // TODO come up with correct format for resource id
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Relay/namespaces/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            "resource id");

    try {
      // TODO this logic for getting azure relay is duplicated across multiple steps
      String landingZoneId = landingZoneApiDispatch.getLandingZoneId(azureCloudContext);
      Optional<ApiAzureLandingZoneDeployedResource> azureRelayResource =
          landingZoneApiDispatch
              .listAzureLandingZoneResources(landingZoneId)
              .getResources()
              .stream()
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

      String relayNamespaceName = azureRelayResource.get().getResourceName();

      logger.info("Attempting to delete Relay Namespace " + azureResourceId);
      manager.hybridConnections().deleteById(azureResourceId);
      manager
          .hybridConnections()
          .delete(
              azureCloudContext.getAzureResourceGroupId(),
              relayNamespaceName,
              resource.getHybridConnectionName());
      return StepResult.getStepResultSuccess();
    } catch (Exception ex) {
      logger.info(
          "Attempt to delete Azure Hybrid Connection failed on this try: " + azureResourceId, ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure Hybrid Connection resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
