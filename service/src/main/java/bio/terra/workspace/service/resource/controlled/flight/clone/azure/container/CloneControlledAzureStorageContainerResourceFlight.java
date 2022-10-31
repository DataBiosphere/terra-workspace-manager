package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.ClonePolicyAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.create.GetCloudContextStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
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
        WorkspaceFlightMapKeys.ControlledResourceKeys.CLONING_INSTRUCTIONS,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    var sourceResource =
        inputParameters.get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, ControlledResource.class);
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
                    WorkspaceFlightMapKeys.ControlledResourceKeys.CLONING_INSTRUCTIONS,
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
        new GetCloudContextStep(
            destinationWorkspaceId,
            CloudPlatform.AZURE,
            flightBeanBag.getGcpCloudContextService(),
            flightBeanBag.getAzureCloudContextService()),
        RetryRules.shortDatabase());

    // Flight plan
    // 1. Check user has read access to source container
    // 2. Gather controlled resource metadata for source object
    // 3. Copy container definition to new container resource
    // 3. If referenced, bail out as unsupported.
    // 4. Copy files from source container to destination container (TODO)
    addStep(
        new CheckControlledResourceAuthStep(
            sourceResource, flightBeanBag.getControlledResourceMetadataManager(), userRequest),
        RetryRules.shortExponential());

    if (mergePolicies) {
      addStep(
          new ClonePolicyAttributesStep(
              sourceResource.getWorkspaceId(),
              destinationWorkspaceId,
              userRequest,
              flightBeanBag.getTpsApiDispatch()));
    }

    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId()));

    // TODO add step copy source container metadata + attributes

    if (CloningInstructions.COPY_REFERENCE == cloningInstructions) {
      throw new IllegalArgumentException("Cloning referenced azure containers not supported");
    } else if (CloningInstructions.COPY_RESOURCE == cloningInstructions
        || CloningInstructions.COPY_DEFINITION == cloningInstructions) {

      // create the container in the cloud context
      var resourceDao = flightBeanBag.getResourceDao();
      var lzApiDispatch = flightBeanBag.getLandingZoneApiDispatch();
      addStep(
          new RetrieveDestinationStorageAccountResourceIdStep(
              resourceDao, lzApiDispatch, userRequest));
      addStep(
          new CopyAzureStorageContainerDefinitionStep(
              userRequest,
              resourceDao,
              lzApiDispatch,
              sourceContainer,
              flightBeanBag.getControlledResourceService(),
              cloningInstructions));
      if (CloningInstructions.COPY_RESOURCE == cloningInstructions) {
        addStep(new CopyContainerFiles());
      }
    }
  }
}
