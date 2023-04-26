package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.exceptions.ChildrenBlockingDeletionException;
import java.util.UUID;

/** Checks to ensure a workspace has no child resources in Sam before deletion. */
public class EnsureNoWorkspaceChildrenStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final UUID workspaceUuid;

  public EnsureNoWorkspaceChildrenStep(
      SamService samService, AuthenticatedUserRequest userRequest, UUID workspaceUuid) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var children = samService.getWorkspaceChildResources(userRequest, workspaceUuid);
    if (!children.isEmpty()) {
      var blocking =
          children.stream()
              .map(r -> String.format("%s resource %s", r.getResourceTypeName(), r.getResourceId()))
              .toList();
      throw new ChildrenBlockingDeletionException(
          "Child resources blocking workspace deletion", blocking);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // nothing to undo - validation only
    return StepResult.getStepResultSuccess();
  }
}
