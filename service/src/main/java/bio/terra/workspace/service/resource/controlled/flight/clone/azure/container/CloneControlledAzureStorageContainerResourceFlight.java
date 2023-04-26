package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.BlobCopier;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.create.GetAzureCloudContextStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.Optional;
import java.util.UUID;

public class CloneControlledAzureStorageContainerResourceFlight extends Flight {

  public CloneControlledAzureStorageContainerResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightUtils.validateRequiredEntries(
        inputParameters,
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    var sourceResource =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledResource.class);
    var userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    boolean mergePolicies =
        Optional.ofNullable(
                inputParameters.get(WorkspaceFlightMapKeys.MERGE_POLICIES, Boolean.class))
            .orElse(false);
    var cloningInstructions =
        Optional.ofNullable(
                inputParameters.get(
                    WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
                    CloningInstructions.class))
            .orElse(sourceResource.getCloningInstructions());

    final ControlledAzureStorageContainerResource sourceContainer =
        sourceResource.castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
    if (CloningInstructions.COPY_NOTHING == cloningInstructions) {
      addStep(new SetNoOpContainerCloneResponseStep(sourceContainer));
      return;
    }

    // Get the cloud context and store it in the working map
    addStep(
        new GetAzureCloudContextStep(
            destinationWorkspaceId, flightBeanBag.getAzureCloudContextService()),
        RetryRules.shortDatabase());

    // Flight plan
    // 1. Check user has read access to source container
    // 2. Gather controlled resource metadata for source object
    // 3. Check if the container is already present
    // 4. Copy container definition to new container resource
    // 5. If referenced, bail out as unsupported.
    // 6. Copy files from source container to destination container
    addStep(
        new CheckControlledResourceAuthStep(
            sourceResource, flightBeanBag.getControlledResourceMetadataManager(), userRequest),
        RetryRules.shortExponential());

    if (mergePolicies) {
      addStep(
          new MergePolicyAttributesStep(
              sourceResource.getWorkspaceId(),
              destinationWorkspaceId,
              cloningInstructions,
              flightBeanBag.getTpsApiDispatch()));
    }

    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId()));

    // check that the container does not already exist in the workspace
    // so we can reliably retry the copy definition step later on
    addStep(new VerifyContainerResourceDoesNotExist(flightBeanBag.getResourceDao()));

    // TODO WOR-590 add step to copy source container metadata + attributes

    if (CloningInstructions.COPY_REFERENCE == cloningInstructions
        || CloningInstructions.LINK_REFERENCE == cloningInstructions) {
      throw new IllegalArgumentException("Cloning referenced azure containers not supported");
    } else if (CloningInstructions.COPY_RESOURCE == cloningInstructions
        || CloningInstructions.COPY_DEFINITION == cloningInstructions) {

      // create the container in the cloud context
      var resourceDao = flightBeanBag.getResourceDao();
      RetryRule cloudRetry = RetryRules.cloud();
      addStep(
          new GetSharedStorageAccountStep(
              destinationWorkspaceId,
              flightBeanBag.getLandingZoneApiDispatch(),
              flightBeanBag.getSamService()),
          cloudRetry);
      addStep(
          new CopyAzureStorageContainerDefinitionStep(
              flightBeanBag.getSamService(),
              userRequest,
              sourceContainer,
              flightBeanBag.getControlledResourceService(),
              cloningInstructions),
          RetryRules.cloud());
      if (CloningInstructions.COPY_RESOURCE == cloningInstructions) {
        var azureStorageService =
            new AzureStorageAccessService(
                flightBeanBag.getSamService(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getStorageAccountKeyProvider(),
                flightBeanBag.getControlledResourceMetadataManager(),
                flightBeanBag.getLandingZoneApiDispatch(),
                flightBeanBag.getAzureCloudContextService(),
                flightBeanBag.getFeatureConfiguration(),
                flightBeanBag.getAzureConfig());
        addStep(
            new CopyAzureStorageContainerBlobsStep(
                azureStorageService,
                sourceContainer,
                resourceDao,
                userRequest,
                new BlobCopier(azureStorageService, userRequest)));
      }
    }
  }
}
