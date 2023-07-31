package bio.terra.workspace.service.resource.controlled.flight.clone;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;

/**
 * This step validates that the provided user has access to read the provided resource. Unlike other
 * flights, this is handled inside a step instead of before the flight because the containing flight
 * is sometimes launched from within another flight, where it's hard to run pre-flight validation.
 *
 * <p>Preconditions: Resolved cloning instructions are COPY_RESOURCE or COPY_DEFINITION. Source
 * resource exists and user request is valid.
 *
 * <p>Post conditions: No side effects, but validation has occured successfully, or we fail with a
 * ForbiddenException.
 */
public class CheckControlledResourceAuthStep implements Step {

  private final ControlledResource resource;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;
  private final AuthenticatedUserRequest userRequest;

  public CheckControlledResourceAuthStep(
      ControlledResource resource,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      AuthenticatedUserRequest userRequest) {
    this.resource = resource;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // Validate caller can read the source resource before proceeding with the flight.
    controlledResourceMetadataManager.validateControlledResourceReadAccess(
        userRequest, resource.getWorkspaceId(), resource.getResourceId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // This step is read-only, so nothing to undo.
    return StepResult.getStepResultSuccess();
  }
}
