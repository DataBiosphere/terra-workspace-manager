package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.BlobCopier;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.CloneControlledAzureResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.StepRetryRulePair;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CloneControlledAzureStorageContainerResourceFlight
    extends CloneControlledAzureResourceFlight {

  public CloneControlledAzureStorageContainerResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
  }

  protected List<StepRetryRulePair> copyDefinition(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    var sourceContainer =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledAzureStorageContainerResource.class);
    var userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    var cloningInstructions =
        Optional.ofNullable(
                inputParameters.get(
                    WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
                    CloningInstructions.class))
            .orElse(sourceContainer.getCloningInstructions());

    return List.of(
        new StepRetryRulePair(
            new GetSharedStorageAccountStep(
                destinationWorkspaceId,
                flightBeanBag.getLandingZoneApiDispatch(),
                flightBeanBag.getSamService(),
                flightBeanBag.getWorkspaceService()),
            RetryRules.cloud()),
        new StepRetryRulePair(
            new CopyAzureStorageContainerDefinitionStep(
                flightBeanBag.getSamService(),
                userRequest,
                sourceContainer,
                flightBeanBag.getControlledResourceService(),
                cloningInstructions),
            RetryRules.cloud()));
  }

  protected List<StepRetryRulePair> copyResource(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    var sourceContainer =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledAzureStorageContainerResource.class);
    var userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    var resourceDao = flightBeanBag.getResourceDao();
    var azureStorageService =
        new AzureStorageAccessService(
            flightBeanBag.getSamService(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getStorageAccountKeyProvider(),
            flightBeanBag.getControlledResourceMetadataManager(),
            flightBeanBag.getLandingZoneApiDispatch(),
            flightBeanBag.getAzureCloudContextService(),
            flightBeanBag.getFeatureConfiguration(),
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getWorkspaceService());
    return List.of(
        new StepRetryRulePair(
            new CopyAzureStorageContainerBlobsStep(
                azureStorageService,
                sourceContainer,
                resourceDao,
                userRequest,
                new BlobCopier(azureStorageService, userRequest)),
            RetryRules.cloud()));
  }
}
