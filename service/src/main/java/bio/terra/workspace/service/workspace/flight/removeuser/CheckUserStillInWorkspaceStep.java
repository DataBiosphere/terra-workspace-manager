package bio.terra.workspace.service.workspace.flight.removeuser;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

public class CheckUserStillInWorkspaceStep implements Step {

  private final UUID workspaceUuid;
  private final SamService samService;
  private final String removedUserEmail;

  public CheckUserStillInWorkspaceStep(
      UUID workspaceUuid, String removedUserEmail, SamService samService) {
    this.workspaceUuid = workspaceUuid;
    this.samService = samService;
    this.removedUserEmail = removedUserEmail;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    boolean userCanReadWorkspace =
        samService.checkAuthAsWsmSa(
            SamConstants.SamResource.WORKSPACE,
            workspaceUuid.toString(),
            SamConstants.SamWorkspaceAction.READ,
            removedUserEmail);
    boolean userCanWriteWorkspace =
        samService.checkAuthAsWsmSa(
            SamConstants.SamResource.WORKSPACE,
            workspaceUuid.toString(),
            SamConstants.SamWorkspaceAction.WRITE,
            removedUserEmail);
    FlightMap workingMap = context.getWorkingMap();
    workingMap.put(ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, userCanReadWorkspace);
    workingMap.put(ControlledResourceKeys.REMOVED_USER_CAN_WRITE, userCanWriteWorkspace);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // This step is only writes to the flight map, so nothing to undo.
    return StepResult.getStepResultSuccess();
  }
}
