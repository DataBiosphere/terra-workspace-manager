package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class CloneReferencedResourceStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final SamService samService;
  private final ReferencedResourceService referencedResourceService;
  private final ReferencedResource resource;
  private final UUID destinationResourceId;
  private final UUID destinationFolderId;

  public CloneReferencedResourceStep(
      AuthenticatedUserRequest userRequest,
      SamService samService,
      ReferencedResourceService referencedResourceService,
      ReferencedResource resource,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId) {
    this.userRequest = userRequest;
    this.samService = samService;
    this.referencedResourceService = referencedResourceService;
    this.resource = resource;
    this.destinationResourceId = destinationResourceId;
    this.destinationFolderId = destinationFolderId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    WsmResourceCloneDetails cloneDetails =
        new WsmResourceCloneDetails()
            .setStewardshipType(StewardshipType.REFERENCED)
            .setResourceType(resource.getResourceType())
            .setSourceResourceId(resource.getResourceId())
            .setName(resource.getName())
            .setDescription(resource.getDescription());
    if (CloningInstructions.COPY_REFERENCE == resource.getCloningInstructions()
        || CloningInstructions.LINK_REFERENCE == resource.getCloningInstructions()) {
      FlightUtils.validateRequiredEntries(
          context.getInputParameters(),
          ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
          JobMapKeys.AUTH_USER_INFO.getKeyName());
      var destinationWorkspaceId =
          context
              .getInputParameters()
              .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

      ReferencedResource destinationResource =
          resource
              .buildReferencedClone(
                  destinationWorkspaceId,
                  destinationResourceId,
                  destinationFolderId,
                  resource.getName(),
                  resource.getDescription(),
                  samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest))
              .castToReferencedResource();

      cloneDetails
          .setResourceType(destinationResource.getResourceType())
          .setStewardshipType(destinationResource.getStewardshipType())
          .setCloningInstructions(destinationResource.getCloningInstructions())
          .setDestinationResourceId(destinationResource.getResourceId());

      // put the destination resource in the map, because it's not communicated
      // from the flight as the response (and we need the workspace ID)
      context
          .getWorkingMap()
          .put(ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, destinationResource);

      try {
        referencedResourceService.createReferenceResourceForClone(
            destinationResource.castToReferencedResource());
        cloneDetails.setResult(WsmCloneResourceResult.SUCCEEDED);
      } catch (Exception e) {
        cloneDetails.setResult(WsmCloneResourceResult.FAILED).setErrorMessage(e.getMessage());
      }
    } else {
      cloneDetails
          .setResult(WsmCloneResourceResult.SKIPPED)
          .setCloningInstructions(resource.getCloningInstructions())
          .setDestinationResourceId(null)
          .setErrorMessage(null);
    }
    // add to the map
    addCloneDetailsToWorkingMap(context, cloneDetails);
    return StepResult.getStepResultSuccess();
  }

  private void addCloneDetailsToWorkingMap(
      FlightContext context, WsmResourceCloneDetails cloneDetails) {
    var resourceIdToResult =
        Optional.ofNullable(
                context
                    .getWorkingMap()
                    .get(
                        ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT,
                        new TypeReference<Map<UUID, WsmResourceCloneDetails>>() {}))
            .orElseGet(HashMap::new);
    resourceIdToResult.put(resource.getResourceId(), cloneDetails);
    context
        .getWorkingMap()
        .put(ControlledResourceKeys.RESOURCE_ID_TO_CLONE_RESULT, resourceIdToResult);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    if (context
        .getWorkingMap()
        .containsKey(ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE)) {
      ReferencedResource resource =
          context
              .getWorkingMap()
              .get(
                  ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, ReferencedResource.class);
      referencedResourceService.deleteReferenceResourceForResourceType(
          resource.getWorkspaceId(),
          resource.getResourceId(),
          resource.getResourceType(),
          userRequest);
    }

    return StepResult.getStepResultSuccess();
  }
}
