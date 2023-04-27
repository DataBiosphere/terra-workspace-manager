package bio.terra.workspace.service.workspace.flight.delete.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteWorkspaceAuthzStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteWorkspaceAuthzStep.class);
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final UUID workspaceUuid;

  public DeleteWorkspaceAuthzStep(
      SamService samService, AuthenticatedUserRequest userRequest, UUID workspaceUuid) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws RetryException, InterruptedException {
    samService.deleteWorkspace(userRequest, workspaceUuid);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Sam does not allow Workspace ID re-use, so a delete really can't be undone. We retry on Sam
    // API errors in the do-step to try avoiding the undo step, but if we get this far there's
    // nothing to do but tell Stairway we're stuck and surface the error from the DO step.
    logger.error("Unable to undo deletion of workspace {} in WSM DB", workspaceUuid);
    return flightContext.getResult();
  }
}
