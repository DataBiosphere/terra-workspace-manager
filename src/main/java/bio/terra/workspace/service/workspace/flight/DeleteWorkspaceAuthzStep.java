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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DeleteWorkspaceAuthzStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteWorkspaceAuthzStep.class);
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
      // TODO: there is no retry rule defined for this step, so it is never retried.
      if (e.getApiExceptionStatus() != HttpStatus.NOT_FOUND.value()) {
        logger.warn(
            String.format(
                "Signaling retry for unexpected Sam error: code (%d) error: %s",
                e.getApiExceptionStatus(), e.toString()),
            e);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
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
