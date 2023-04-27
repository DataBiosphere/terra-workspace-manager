package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for checking that a user is authorized to read an existing workspace. */
public class CheckSamWorkspaceAuthzStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final Workspace workspace;

  private final Logger logger = LoggerFactory.getLogger(CheckSamWorkspaceAuthzStep.class);

  public CheckSamWorkspaceAuthzStep(
      Workspace workspace, SamService samService, AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.workspace = workspace;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    UUID workspaceUuid = workspace.getWorkspaceId();
    if (!canReadExistingWorkspace(workspaceUuid)) {
      throw new WorkspaceNotFoundException(
          String.format(
              "Sam resource not found for workspace %s. WSM requires an existing Sam resource for a RAWLS_WORKSPACE.",
              workspaceUuid));
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private boolean canReadExistingWorkspace(UUID workspaceUuid) throws InterruptedException {
    return samService.isAuthorized(
        userRequest,
        SamConstants.SamResource.WORKSPACE,
        workspaceUuid.toString(),
        SamConstants.SamWorkspaceAction.READ);
  }
}
