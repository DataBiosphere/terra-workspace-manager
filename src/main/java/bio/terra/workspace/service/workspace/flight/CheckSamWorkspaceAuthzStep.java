package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for checking that a user is authorized to read an existing workspace. */
public class CheckSamWorkspaceAuthzStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userReq;

  private final Logger logger = LoggerFactory.getLogger(CreateWorkspaceAuthzStep.class);

  public CheckSamWorkspaceAuthzStep(SamService samService, AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.userReq = userReq;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    UUID workspaceID =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    if (!canReadExistingWorkspace(workspaceID)) {
      throw new WorkspaceNotFoundException(
          String.format(
              "Sam resource not found for workspace %s. WSM requires an existing Sam resource for a RAWLS_WORKSPACE.",
              workspaceID));
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private boolean canReadExistingWorkspace(UUID workspaceID) {
    return samService.isAuthorized(
        userReq.getRequiredToken(),
        SamConstants.SAM_WORKSPACE_RESOURCE,
        workspaceID.toString(),
        SamConstants.SAM_WORKSPACE_READ_ACTION);
  }
}
