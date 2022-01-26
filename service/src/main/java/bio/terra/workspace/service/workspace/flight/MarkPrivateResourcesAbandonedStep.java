package bio.terra.workspace.service.workspace.flight;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Step for marking all of user's private resources in a workspace as ABANDONED. This doesn't change
 * any permissions (that happens in the previous {@code RemovePrivateResourceAccessStep}), but makes
 * it clear to WSM and other workspace users that no users have access to this resource.
 */
public class MarkPrivateResourcesAbandonedStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final String userEmailToRemove;

  public MarkPrivateResourcesAbandonedStep(
      UUID workspaceId, String userEmailToRemove, ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.userEmailToRemove = userEmailToRemove;
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
    // If the user has been fully removed from the workspace, mark all their private resources as
    // abandoned.
    resourceDao.setPrivateResourcesStateForWorkspaceUser(
        workspaceId, userEmailToRemove, PrivateResourceState.ABANDONED);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    boolean userStillInWorkspace =
        workingMap.get(ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, Boolean.class);
    // This flight is triggered whenever a user loses any role on a workspace. If they are still
    // a member of the workspace via a group or another role, we do not need to remove their access
    // to private resources.
    if (userStillInWorkspace) {
      return StepResult.getStepResultSuccess();
    }

    List<ResourceRolePair> resourceRolePairs =
        workingMap.get(ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});
    List<ControlledResource> uniqueControlledResources =
        resourceRolePairs.stream()
            .map(ResourceRolePair::getResource)
            .distinct()
            .collect(Collectors.toList());
    for (ControlledResource resource : uniqueControlledResources) {
      PrivateResourceState privateResourceState =
          resource
              .getPrivateResourceState()
              .orElseThrow(
                  () ->
                      new InconsistentFieldsException(
                          "Received private resource without private resource state set"));
      resourceDao.setPrivateResourceState(resource, privateResourceState);
    }
    return StepResult.getStepResultSuccess();
  }
}
