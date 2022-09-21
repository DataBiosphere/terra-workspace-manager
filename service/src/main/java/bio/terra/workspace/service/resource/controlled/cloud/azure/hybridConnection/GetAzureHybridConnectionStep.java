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
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.relay.RelayManager;

import java.util.Optional;

/**
 * Gets an Azure RelayNamespace, and fails if it already exists. This step is designed to run
 * immediately before {@link CreateAzureHybridConnectionStep} to ensure idempotency of the create
 * operation.
 */
public class GetAzureHybridConnectionStep implements Step {

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final ControlledAzureHybridConnectionResource resource;

  public GetAzureHybridConnectionStep(
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
      String landingZoneId = landingZoneApiDispatch.getLandingZoneId(azureCloudContext);
      Optional<ApiAzureLandingZoneDeployedResource> azureRelayResource = landingZoneApiDispatch
          .listAzureLandingZoneResources(landingZoneId)
          .getResources()
          .stream()
          .filter(purposeGroup -> purposeGroup.getPurpose().equals(ResourcePurpose.SHARED_RESOURCE.toString()))
          .findFirst()
          .flatMap(purposeGroup -> purposeGroup
              .getDeployedResources()
              .stream()
              .filter(deployedResource -> deployedResource.getResourceType().equals("Azure Relay Type")) // TODO
              .findFirst());

      String relayNamespaceName = azureRelayResource.get().getResourceName();

      manager // TODO get hcName
          .hybridConnections()
          .get(azureCloudContext.getAzureResourceGroupId(), relayNamespaceName, resource.getHybridConnectionName());
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure Hybrid Connection with name %s already exists in resource group %s",
                  azureCloudContext.getAzureResourceGroupId(), resource.getName())));
    } catch (ManagementException e) { // TODO add LZ exceptions to this
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
