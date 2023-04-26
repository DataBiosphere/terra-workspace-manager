package bio.terra.workspace.service.workspace.flight.removeuser;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClaimUserPrivateResourcesStep implements Step {

  private final UUID workspaceUuid;
  private final String userEmail;
  private final ResourceDao resourceDao;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;

  public ClaimUserPrivateResourcesStep(
      UUID workspaceUuid,
      String userEmail,
      ResourceDao resourceDao,
      SamService samService,
      AuthenticatedUserRequest userRequest) {
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
    this.userEmail = userEmail;
    this.samService = samService;
    this.userRequest = userRequest;
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
    // Read the list of resources this user owns from WSM's DB which are not being cleaned up by
    // other flights and indicate this flight is cleaning them up.
    List<ControlledResource> userResources =
        resourceDao.claimCleanupForWorkspacePrivateResources(
            workspaceUuid, userEmail, context.getFlightId());
    // For each private resource, query Sam to find the roles the user has.
    List<ResourceRolePair> resourceRolesToRemove = new ArrayList<>();
    for (ControlledResource resource : userResources) {
      List<ControlledResourceIamRole> userRoles =
          samService.getUserRolesOnPrivateResource(resource, userEmail, userRequest);
      for (ControlledResourceIamRole role : userRoles) {
        resourceRolesToRemove.add(new ResourceRolePair(resource, role));
      }
    }
    workingMap.put(ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, resourceRolesToRemove);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // This only releases resources claimed by the current flight, so it cannot overwrite claims
    // from
    // other flights.
    resourceDao.releasePrivateResourceCleanupClaims(
        workspaceUuid, userEmail, context.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
