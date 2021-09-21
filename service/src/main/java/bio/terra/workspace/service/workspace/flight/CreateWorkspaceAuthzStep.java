package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
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

  private final Logger logger = LoggerFactory.getLogger(CreateWorkspaceAuthzStep.class);

  public CreateWorkspaceAuthzStep(SamService samService, AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws RetryException, InterruptedException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID =
        UUID.fromString(inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    // Even though WSM should own this resource, Stairway steps can run multiple times, so it's
    // possible this step already created the resource. If WSM can either read the existing Sam
    // resource or create a new one, this is considered successful.
    if (!canReadExistingWorkspace(workspaceID)) {
      samService.createWorkspaceWithDefaults(userRequest, workspaceID);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID =
        UUID.fromString(inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    samService.deleteWorkspace(userRequest, workspaceID);
    return StepResult.getStepResultSuccess();
  }

  private boolean canReadExistingWorkspace(UUID workspaceID) throws InterruptedException {
    return samService.isAuthorized(
        userRequest,
        SamConstants.SAM_WORKSPACE_RESOURCE,
        workspaceID.toString(),
        SamConstants.SAM_WORKSPACE_READ_ACTION);
  }
}
