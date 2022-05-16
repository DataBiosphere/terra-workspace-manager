package bio.terra.workspace.service.workspace.flight;

import bio.terra.common.sam.exception.SamConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step that creates a Sam workspace resource. This only runs for MC_WORKSPACE stage workspaces,
 * as RAWLS_WORKSPACEs use existing Sam resources instead.
 */
public class CreateWorkspaceAuthzStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final Workspace workspace;

  private final Logger logger = LoggerFactory.getLogger(CreateWorkspaceAuthzStep.class);

  public CreateWorkspaceAuthzStep(
      Workspace workspace, SamService samService, AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.userRequest = userRequest;
    this.workspace = workspace;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws RetryException, InterruptedException {

    try {
      samService.createWorkspaceWithDefaults(userRequest, workspace.getWorkspaceId());
    } catch (SamConflictException e) {
      // Stairway steps can run multiple times. This step must have run before.
      return StepResult.getStepResultSuccess();
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    samService.deleteWorkspace(userRequest, workspace.getWorkspaceId());
    return StepResult.getStepResultSuccess();
  }
}
