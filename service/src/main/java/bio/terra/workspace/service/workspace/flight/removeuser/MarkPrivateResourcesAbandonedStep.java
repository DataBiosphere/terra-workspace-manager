package bio.terra.workspace.service.workspace.flight.removeuser;

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
import java.util.Optional;
import java.util.UUID;

/**
 * Step for marking all of user's private resources in a workspace as ABANDONED. This doesn't change
 * any permissions (that happens in the previous {@code RemovePrivateResourceAccessStep}), but makes
 * it clear to WSM and other workspace users that no users have access to this resource.
 */
public class MarkPrivateResourcesAbandonedStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final String userEmailToRemove;

  public MarkPrivateResourcesAbandonedStep(
      UUID workspaceUuid, String userEmailToRemove, ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
    this.userEmailToRemove = userEmailToRemove;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // mark claimed private resources as abandoned.
    resourceDao.setPrivateResourcesStateForWorkspaceUser(
        workspaceUuid,
        userEmailToRemove,
        PrivateResourceState.ABANDONED,
        Optional.of(context.getFlightId()));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    List<ResourceRolePair> resourceRolePairs =
        workingMap.get(ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});
    List<ControlledResource> uniqueControlledResources =
        resourceRolePairs.stream().map(ResourceRolePair::getResource).distinct().toList();
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
