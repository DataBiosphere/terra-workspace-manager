package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.util.UUID;

public class RemoveUserFromSamStep implements Step {

  private final SamService samService;
  private final UUID workspaceUuid;
  private final WsmIamRole roleToRemove;
  private final String userToRemoveEmail;
  private final AuthenticatedUserRequest userRequest;

  public RemoveUserFromSamStep(
      UUID workspaceUuid,
      WsmIamRole roleToRemove,
      String userToRemoveEmail,
      SamService samService,
      AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.workspaceUuid = workspaceUuid;
    this.roleToRemove = roleToRemove;
    this.userToRemoveEmail = userToRemoveEmail;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // Sam returns a 204 regardless of whether the user was actually removed or not, so this step is
    // always idempotent.
    samService.removeWorkspaceRole(workspaceUuid, userRequest, roleToRemove, userToRemoveEmail);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Sam de-duplicates policy membership, so it's safe to restore roles that may not have been
    // removed in the DO step.
    samService.grantWorkspaceRole(workspaceUuid, userRequest, roleToRemove, userToRemoveEmail);
    return StepResult.getStepResultSuccess();
  }
}
