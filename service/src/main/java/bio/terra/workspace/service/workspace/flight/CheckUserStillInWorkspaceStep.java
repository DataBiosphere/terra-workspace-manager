package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

public class CheckUserStillInWorkspaceStep implements Step {

  private final UUID workspaceId;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final String removedUserEmail;

  public CheckUserStillInWorkspaceStep(
      UUID workspaceId,
      String removedUserEmail,
      SamService samService,
      AuthenticatedUserRequest userRequest) {
    this.workspaceId = workspaceId;
    this.samService = samService;
    this.userRequest = userRequest;
    this.removedUserEmail = removedUserEmail;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    boolean userCanReadWorkspace =
        samService.userIsAuthorized(
            SamConstants.SAM_WORKSPACE_RESOURCE,
            workspaceId.toString(),
            SamConstants.SAM_WORKSPACE_READ_ACTION,
            removedUserEmail,
            userRequest);
    FlightMap workingMap = context.getWorkingMap();
    workingMap.put(ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, userCanReadWorkspace);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // This step is only writes to the flight map, so nothing to undo.
    return StepResult.getStepResultSuccess();
  }
}
