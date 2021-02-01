package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class DeleteWorkspaceAuthzStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userReq;

  public DeleteWorkspaceAuthzStep(SamService samService, AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.userReq = userReq;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    try {
      samService.deleteWorkspace(userReq.getRequiredToken(), workspaceID);
    } catch (SamApiException e) {
      // Stairway steps may run multiple times, so this may already have been deleted.
      // For all other errors we should always retry because there's no way to undo a Sam delete.
      if (e.getApiExceptionStatus() != HttpStatus.NOT_FOUND.value()) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Sam does not allow Workspace ID re-use, so a delete really can't be undone. We retry on Sam
    // API errors in the do-step to try avoiding the undo step, but if we get this far there's
    // nothing to do but tell Stairway we're stuck.
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
