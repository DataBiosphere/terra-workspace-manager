package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    UUID workspaceID =
        UUID.fromString(inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    // Sam Service will take care of retrying any retry-able errors. Failures will be bubbled up
    // as derivatives of ErrorReportException.
    samService.deleteWorkspace(userReq.getRequiredToken(), workspaceID);
    return StepResult.getStepResultSuccess().getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Sam does not allow Workspace ID re-use, so a delete really can't be undone. We retry on Sam
    // API errors in the do-step to try avoiding the undo step, but if we get this far there's
    // nothing to do but tell Stairway we're stuck.
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
