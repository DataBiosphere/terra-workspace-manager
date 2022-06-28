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
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

public class LaunchCreateReferenceResourceFlightStep implements Step {

  private final ReferencedResourceService referencedResourceService;
  private final ReferencedResource resource;
  private final String subflightId;
  private final UUID destinationResourceId;

  public LaunchCreateReferenceResourceFlightStep(
      ReferencedResourceService referencedResourceService,
      ReferencedResource resource,
      String subflightId,
      UUID destinationResourceId) {
    this.referencedResourceService = referencedResourceService;
    this.resource = resource;
    this.subflightId = subflightId;
    this.destinationResourceId = destinationResourceId;
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
    final var destinationWorkspaceId =
        context
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    final ReferencedResource destinationResource =
        WorkspaceCloneUtils.buildDestinationReferencedResource(
            resource,
            destinationWorkspaceId,
            destinationResourceId,
            resource.getName(),
            resource.getDescription());

    // put the destination resource in the map, because it's not communicated
    // from the flight as the response (and we need the workspace ID)
    context
        .getWorkingMap()
        .put(ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE, destinationResource);

    final FlightMap subflightInputParameters = new FlightMap();
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
