package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InvalidRemoveUserRequestException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.util.List;
import java.util.UUID;

public class ValidateUserRoleStep implements Step {

  private final SamService samService;
  private final UUID workspaceUuid;
  private final WsmIamRole roleToRemove;
  private final String userToRemoveEmail;
  private final AuthenticatedUserRequest userRequest;

  public ValidateUserRoleStep(
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
    // Validate that the user being removed is a direct member of the specified role. Users may also
    // be added to a workspace via managed groups, but WSM does not control membership of those
    // groups, and so cannot remove them here.
    List<String> roleMembers =
        samService.listUsersWithWorkspaceRole(workspaceUuid, roleToRemove, userRequest).stream()
            // SAM does not always use lowercase emails, so lowercase everything here before the
            // contains check below
            .map(String::toLowerCase)
            .toList();
    if (!roleMembers.contains(userToRemoveEmail)) {
      // If the above Sam call succeeds, the caller has permission to view workspace members, so
      // we do not need to hide workspace member information by always returning 204.
      throw new InvalidRemoveUserRequestException(
          String.format(
              "The specified user does not directly have the %s role in workspace %s. They may need to be removed from a group instead.",
              roleToRemove.toSamRole(), workspaceUuid));
    }
    // Additionally, validate that the user is not removing themselves as the sole owner. WSM does
    // not allow users to abandon resources this way.
    if (roleToRemove.equals(WsmIamRole.OWNER) && roleMembers.size() == 1) {
      throw new InvalidRemoveUserRequestException(
          "You may not remove yourself as the sole workspace owner. Grant another user the workspace owner role before removing yourself.");
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // This step is validation only, nothing to undo.
    return StepResult.getStepResultSuccess();
  }
}
