package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;
import bio.terra.workspace.service.workspace.model.WsmResourceCloneDetails;
import java.util.UUID;

public class LaunchCloneReferenceResourceFlightStep implements Step {

  private final ReferencedResourceService referencedResourceService;
  private final ReferencedResource resource;
  private final String subflightId;

  public LaunchCloneReferenceResourceFlightStep(
      ReferencedResourceService referencedResourceService,
      ReferencedResource resource,
      String subflightId) {
    this.referencedResourceService = referencedResourceService;
    this.resource = resource;
    this.subflightId = subflightId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final var destinationWorkspaceId =
        context
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final String description =
        String.format("Clone of Referenced Resource %s", resource.getResourceId());
    // TODO(jaycarlton): PF-918 don't reuse the service call; launch subflight directly
    final ReferencedResource clonedResource =
        referencedResourceService.cloneReferencedResource(
            resource, destinationWorkspaceId, null, description, userRequest);
    final WsmResourceCloneDetails result = new WsmResourceCloneDetails();
    result.setResourceType(resource.getResourceType());
    result.setStewardshipType(resource.getStewardshipType());
    result.setDestinationResourceId(clonedResource.getResourceId());
    result.setResult(WsmCloneResourceResult.SUCCEEDED);
    result.setCloningInstructions(
        CloningInstructions
            .COPY_REFERENCE); // FIXME(jaycarlton) reference clone doesn't use cloning instructions

    // put result in the output map
    return StepResult.getStepResultSuccess();
  }

  // No need to undo here as entire workspace will be destroyed by earlier undo
  // method.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
