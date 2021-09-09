package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ReadUserPrivateResourcesStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final String userEmail;

  public ReadUserPrivateResourcesStep(UUID workspaceId, String userEmail, ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.userEmail = userEmail;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    boolean userStillInWorkspace =
        Optional.ofNullable(
                workingMap.get(
                    ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, Boolean.class))
            .orElse(false);
    // This flight is triggered whenever a user loses any role on a workspace. If they are still
    // a member of the workspace via a group or another role, we do not need to remove their access
    // to private resources.
    if (userStillInWorkspace) {
      return StepResult.getStepResultSuccess();
    }
    List<ControlledResource> userResources =
        resourceDao.listPrivateResourcesByUser(workspaceId, userEmail);
    workingMap.put(ControlledResourceKeys.REVOCABLE_PRIVATE_RESOURCES, userResources);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Do nothing, this step only populates the working map.
    return StepResult.getStepResultSuccess();
  }
}
