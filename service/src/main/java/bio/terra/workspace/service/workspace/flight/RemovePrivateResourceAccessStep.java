package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemovePrivateResourceAccessStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final String userToRemove;

  private final Logger logger = LoggerFactory.getLogger(RemovePrivateResourceAccessStep.class);

  public RemovePrivateResourceAccessStep(
      String userToRemove, SamService samService, AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.userToRemove = userToRemove;
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
        workingMap.get(
            ControlledResourceKeys.REVOCABLE_PRIVATE_RESOURCES, new TypeReference<>() {});
    for (ControlledResource resource : userResources) {
      // WSM does not store which roles user have on their private resources, so here we attempt
      // to remove them from all possible roles.
      samService.removeResourceRole(
          resource, userRequest, ControlledResourceIamRole.READER, userToRemove);
      samService.removeResourceRole(
          resource, userRequest, ControlledResourceIamRole.WRITER, userToRemove);
      samService.removeResourceRole(
          resource, userRequest, ControlledResourceIamRole.EDITOR, userToRemove);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // WSM does not store which roles users have on their private resources, so there is no way
    // to undo any removal that happened in the DO step. The best we can do here is log an error and
    // surface whatever failure led to this undo.
    logger.error("Unable to undo resource access removal for user {}", userToRemove);
    return context.getResult();
  }
}
