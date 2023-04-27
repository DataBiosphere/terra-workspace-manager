package bio.terra.workspace.service.workspace.flight.removeuser;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

public class RemovePrivateResourceAccessStep implements Step {

  private final SamService samService;
  private final String userToRemove;

  public RemovePrivateResourceAccessStep(String userToRemove, SamService samService) {
    this.samService = samService;
    this.userToRemove = userToRemove;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    boolean userStillInWorkspace =
        workingMap.get(ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, Boolean.class);
    // This flight is triggered whenever a user loses any role on a workspace. If they are still
    // a member of the workspace via a group or another role, we do not need to remove their access
    // to private resources.
    if (userStillInWorkspace) {
      return StepResult.getStepResultSuccess();
    }

    List<ResourceRolePair> resourceRolesToRemove =
        workingMap.get(ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});
    for (ResourceRolePair resourceRolePair : resourceRolesToRemove) {
      samService.removeResourceRole(
          resourceRolePair.getResource(), resourceRolePair.getRole(), userToRemove);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    boolean userStillInWorkspace =
        workingMap.get(ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, Boolean.class);
    // This flight is triggered whenever a user loses any role on a workspace. If they are still
    // a member of the workspace via a group or another role, we do not need to restore their access
    // to private resources here.
    if (userStillInWorkspace) {
      return StepResult.getStepResultSuccess();
    }

    // Restore all roles removed in the DO step.
    List<ResourceRolePair> resourceRolesToRestore =
        workingMap.get(ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});
    for (ResourceRolePair resourceRolePair : resourceRolesToRestore) {
      // Sam de-duplicates policy membership, so it's safe to restore roles that may not have been
      // removed in the DO step.
      samService.restoreResourceRole(
          resourceRolePair.getResource(), resourceRolePair.getRole(), userToRemove);
    }
    return StepResult.getStepResultSuccess();
  }
}
