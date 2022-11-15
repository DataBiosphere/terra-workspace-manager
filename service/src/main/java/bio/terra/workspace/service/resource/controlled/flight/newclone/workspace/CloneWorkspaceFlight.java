package bio.terra.workspace.service.resource.controlled.flight.newclone.workspace;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.common.utils.WsmFlight;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.flight.clone.ClonePolicyAttributesStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloneIds;
import bio.terra.workspace.service.workspace.model.CloneSourceMetadata;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;

import java.util.UUID;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CLONE_IDS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CLONE_SOURCE_METADATA;

public class CloneWorkspaceFlight extends WsmFlight {
  // Inputs:
  //   userRequest - credentials of the cloning user
  //   cloneSourceMetadata - all of the source workspace metadata
  //   destinationWorkspace - destination workspace already created
  //   location - destination location for cloned controlled resources
  //   cloneIds - holds maps of source id to target id for resources and folders

  // Flight Plan
  //  1. Set the destination workspace policies from the source workspace
  //  2. Make the cloud contexts in the destination workspace
  //  3. Create the folders in the destination workspace
  //  4. Compute all destination resources
  //  5. Clone the resources in sub-flights (could be in parallel)

  public CloneWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    // Retry Rules
    var cloudRetryRule = RetryRules.cloud();

    // Input parameters
    var userRequest =
        getInputRequired(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var cloneSourceMetadata = getInputRequired(CLONE_SOURCE_METADATA, CloneSourceMetadata.class);
    var cloneIds = getInputRequired(CLONE_IDS, CloneIds.class);
    var destinationWorkspace =
        getInputRequired(WorkspaceFlightMapKeys.DESTINATION_WORKSPACE, Workspace.class);
    var location =
        getInputRequiredNullable(
            WorkspaceFlightMapKeys.ControlledResourceKeys.LOCATION, String.class);

    Workspace sourceWorkspace = cloneSourceMetadata.getWorkspace();

    // -- policies --
    // Copy policies when TPS is enabled and the workspace is not RAWLS
    if (beanBag().getFeatureConfiguration().isTpsEnabled()
        && sourceWorkspace.getWorkspaceStage() != WorkspaceStage.RAWLS_WORKSPACE) {
      addStep(
          new ClonePolicyAttributesStep(
              sourceWorkspace.getWorkspaceId(),
              destinationWorkspace.getWorkspaceId(),
              userRequest,
              beanBag().getTpsApiDispatch()),
          cloudRetryRule);
    }

    // -- cloud contexts --
    if (cloneSourceMetadata.getGcpCloudContext() != null) {
      beanBag()
          .getGcpCloudContextService()
          .makeCreateGcpContextSteps(this, destinationWorkspace.getWorkspaceId(), userRequest);
    }

    // TODO: [PF-2107] Do the same fixup for Azure cloud context

    // -- folders --
    // Only add the clone step if there are any folders to deal with
    if (!cloneIds.folderIdMap().isEmpty()) {
      addStep(
          new CloneFoldersStep(
              beanBag().getFolderDao(),
              cloneSourceMetadata,
              destinationWorkspace,
              cloneIds.folderIdMap()));
    }

    // -- referenced resources --
    for (WsmResource resource : cloneSourceMetadata.getReferencedResources()) {
      makeCloneReferenceResourceStep(resource, destinationWorkspace.getWorkspaceId(), cloneIds);
    }

    // -- controlled resources --
    for (WsmResource resource : cloneSourceMetadata.getControlledResources()) {
      makeCloneControlledResourceStep(resource, destinationWorkspace.getWorkspaceId(), cloneIds, userRequest);
    }

  }

  private void makeCloneControlledResourceStep(
      WsmResource resource,
      UUID destinationWorkspaceId,
      CloneIds cloneIds,
      AuthenticatedUserRequest userRequest) {

    UUID destinationFolderId =
      resource.getFolderId().map(id -> cloneIds.folderIdMap().get(id)).orElse(null);

    ControlledResource controlledResource = resource.castToControlledResource();

    // We run the controlled resource clones as separate flights.
    ControlledResourceFields commonFields =
      controlledResource.buildControlledResourceCommonFields(
        destinationWorkspaceId,
        cloneIds.referencedResourceIdMap().get(resource.getResourceId()),
        destinationFolderId,
        resource.getName(),
        resource.getDescription());





  }

  private void makeCloneReferenceResourceStep(
    WsmResource resource, UUID destinationWorkspaceId, CloneIds cloneIds) {
    // Build the destination resource
    UUID destinationFolderId =
        resource.getFolderId().map(id -> cloneIds.folderIdMap().get(id)).orElse(null);
    ReferencedResource destinationResource =
        resource
            .buildReferencedClone(
                destinationWorkspaceId,
                cloneIds.referencedResourceIdMap().get(resource.getResourceId()),
                destinationFolderId,
                resource.getName(),
                resource.getDescription())
            .castToReferencedResource();

    addStep(new CloneReferencedResourceStep(beanBag().getResourceDao(), destinationResource));
  }
}
