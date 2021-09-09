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
  private final UUID workspaceId;
  private final WsmIamRole roleToRemove;
  private final String userToRemoveEmail;
  private final AuthenticatedUserRequest userRequest;

  public RemoveUserFromSamStep(
      UUID workspaceId,
      WsmIamRole roleToRemove,
      String userToRemoveEmail,
      SamService samService,
      AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.workspaceId = workspaceId;
    this.roleToRemove = roleToRemove;
    this.userToRemoveEmail = userToRemoveEmail;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    samService.removeWorkspaceRole(workspaceId, userRequest, roleToRemove, userToRemoveEmail);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    samService.grantWorkspaceRole(workspaceId, userRequest, roleToRemove, userToRemoveEmail);
    return StepResult.getStepResultSuccess();
  }
}
