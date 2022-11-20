package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;
import javax.annotation.Nullable;

public class LaunchCreateReferenceResourceFlightStep implements Step {

  private final ResourceDao resourceDao;
  private final ReferencedResource resource;
  private final UUID destinationResourceId;
  private final UUID destinationFolderId;

  public LaunchCreateReferenceResourceFlightStep(
      ResourceDao resourceDao,
      ReferencedResource resource,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId) {
    this.resourceDao = resourceDao;
    this.resource = resource;
    this.destinationResourceId = destinationResourceId;
    this.destinationFolderId = destinationFolderId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (CloningInstructions.COPY_REFERENCE != resource.getCloningInstructions()) {
      // Nothing to do -- don't launch flight
      return StepResult.getStepResultSuccess();
    }
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
                resource.getDescription())
            .castToReferencedResource();

    // put the destination resource in the map, because it's not communicated
    // from the flight as the response (and we need the workspace ID)
    context
        .getWorkingMap()
        .put(ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, destinationResource);
    resourceDao.createReferencedResource(destinationResource);

    return StepResult.getStepResultSuccess();
  }

  // No need to undo here as entire workspace will be destroyed by earlier undo
  // method.
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
      resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
    }

    return StepResult.getStepResultSuccess();
  }
}
