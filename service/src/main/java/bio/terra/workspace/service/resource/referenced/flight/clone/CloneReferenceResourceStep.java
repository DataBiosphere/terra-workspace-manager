package bio.terra.workspace.service.resource.referenced.flight.clone;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import org.springframework.http.HttpStatus;

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
    try {
      var createdResource =
          referencedResourceService.createReferenceResourceForClone(destinationResource);
      FlightUtils.setResponse(context, createdResource, HttpStatus.OK);
    } catch (Exception e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
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
