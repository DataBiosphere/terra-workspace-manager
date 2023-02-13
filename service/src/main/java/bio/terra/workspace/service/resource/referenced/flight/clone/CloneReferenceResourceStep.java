package bio.terra.workspace.service.resource.referenced.flight.clone;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
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

public class CloneReferenceResourceStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final ReferencedResourceService referencedResourceService;
  private final ReferencedResource sourceResource;
  private final ReferencedResource destinationResource;

  public CloneReferenceResourceStep(
      AuthenticatedUserRequest userRequest,
      ReferencedResourceService referencedResourceService,
      ReferencedResource sourceResource,
      ReferencedResource destinationResource) {
    this.userRequest = userRequest;
    this.referencedResourceService = referencedResourceService;
    this.sourceResource = sourceResource;
    this.destinationResource = destinationResource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    WsmResourceCloneDetails cloneDetails =
        new WsmResourceCloneDetails()
            .setStewardshipType(StewardshipType.REFERENCED)
            .setResourceType(sourceResource.getResourceType())
            .setSourceResourceId(destinationResource.getResourceId())
            .setName(destinationResource.getName())
            .setDescription(destinationResource.getDescription());

    cloneDetails
        .setResourceType(destinationResource.getResourceType())
        .setStewardshipType(destinationResource.getStewardshipType())
        .setCloningInstructions(destinationResource.getCloningInstructions())
        .setDestinationResourceId(destinationResource.getResourceId());

    // put the destination resource in the map, because it's not communicated
    // from the flight as the response.
    context
        .getWorkingMap()
        .put(ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, destinationResource);

    try {
      referencedResourceService.createReferenceResourceForClone(
          destinationResource, sourceResource, userRequest);
      cloneDetails.setResult(WsmCloneResourceResult.SUCCEEDED);
    } catch (Exception e) {
      cloneDetails.setResult(WsmCloneResourceResult.FAILED).setErrorMessage(e.getMessage());
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
    resourceIdToResult.put(sourceResource.getResourceId(), cloneDetails);
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
