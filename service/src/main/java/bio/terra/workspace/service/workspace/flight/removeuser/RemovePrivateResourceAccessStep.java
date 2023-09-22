package bio.terra.workspace.service.workspace.flight.removeuser;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
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
    List<ResourceRolePair> resourceRolesToRemove =
        FlightUtils.getRequired(
            workingMap, ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});
    for (ResourceRolePair resourceRolePair : resourceRolesToRemove) {
      samService.removeResourceRole(
          resourceRolePair.getResource(), resourceRolePair.getRole(), userToRemove);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    // Restore all roles removed in the DO step.
    List<ResourceRolePair> resourceRolesToRestore =
        FlightUtils.getRequired(
            workingMap, ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});
    for (ResourceRolePair resourceRolePair : resourceRolesToRestore) {
      // Sam de-duplicates policy membership, so it's safe to restore roles that may not have been
      // removed in the DO step.
      samService.restoreResourceRole(
          resourceRolePair.getResource(), resourceRolePair.getRole(), userToRemove);
    }
    return StepResult.getStepResultSuccess();
  }
}
