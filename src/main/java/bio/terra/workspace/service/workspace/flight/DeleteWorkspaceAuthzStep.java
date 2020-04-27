package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import java.util.UUID;

public class DeleteWorkspaceAuthzStep implements Step {

  private SamService samService;
  private AuthenticatedUserRequest userReq;

  public DeleteWorkspaceAuthzStep(SamService samService, AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.userReq = userReq;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    samService.deleteWorkspace(userReq.getRequiredToken(), workspaceID);
    return StepResult.getStepResultSuccess();
  }

  // Note: This doesn't fully undo the do-step, as the newly-created IAM resource will not have
  // the same access policies as the old one. However, it prevents strange half-deleted states
  // from occuring.
  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    samService.createWorkspaceWithDefaults(userReq.getRequiredToken(), workspaceID);
    return StepResult.getStepResultSuccess();
  }
}
