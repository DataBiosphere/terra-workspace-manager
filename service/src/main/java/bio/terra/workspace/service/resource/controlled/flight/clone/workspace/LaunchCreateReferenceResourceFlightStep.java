package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceResourceFlight;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;
import javax.annotation.Nullable;

public class LaunchCreateReferenceResourceFlightStep implements Step {

  private final ReferencedResourceService referencedResourceService;
  private final ReferencedResource resource;
  private final String subflightId;
  private final UUID destinationResourceId;
  private final UUID destinationFolderId;

  public LaunchCreateReferenceResourceFlightStep(
      ReferencedResourceService referencedResourceService,
      ReferencedResource resource,
      String subflightId,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId) {
    this.referencedResourceService = referencedResourceService;
    this.resource = resource;
    this.subflightId = subflightId;
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
    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

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

    FlightMap subflightInputParameters = new FlightMap();
    subflightInputParameters.put(ResourceKeys.RESOURCE, destinationResource);
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(
        WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, resource.getResourceType().name());
    // use destination workspace ID so UI can associate this job with the workspace being cloned
    subflightInputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, destinationWorkspaceId);
    subflightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format("Clone referenced resource %s", resource.getResourceId().toString()));
    subflightInputParameters.put(
        ControlledResourceKeys.DESTINATION_RESOURCE_ID, destinationResourceId);
    subflightInputParameters.put(ControlledResourceKeys.DESTINATION_FOLDER_ID, destinationFolderId);
    try {
      context
          .getStairway()
          .submit(subflightId, CreateReferenceResourceFlight.class, subflightInputParameters);
    } catch (DatabaseOperationException | StairwayExecutionException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    } catch (DuplicateFlightIdException unused) {
      // already submitted the flight - treat as success
      return StepResult.getStepResultSuccess();
    }
    FlightUtils.validateRequiredEntries(
        context.getWorkingMap(), ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE);
    return StepResult.getStepResultSuccess();
  }

  // No need to undo here as entire workspace will be destroyed by earlier undo
  // method.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
