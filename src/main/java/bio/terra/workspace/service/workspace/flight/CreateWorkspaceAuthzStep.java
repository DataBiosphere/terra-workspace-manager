package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * A step that creates a Sam workspace resource. This only runs for MC_WORKSPACE stage workspaces,
 * as RAWLS_WORKSPACEs use existing Sam resources instead.
 */
public class CreateWorkspaceAuthzStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userReq;

  private final Logger logger = LoggerFactory.getLogger(CreateWorkspaceAuthzStep.class);

  public CreateWorkspaceAuthzStep(SamService samService, AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.userReq = userReq;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    // Even though WSM should own this resource, Stairway steps can run multiple times, so it's
    // possible this step already created the resource. If WSM can either read the existing Sam
    // resource or create a new one, this is considered successful.
    if (!canReadExistingWorkspace(workspaceID)) {
      samService.createWorkspaceWithDefaults(userReq, workspaceID);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    try {
      samService.deleteWorkspace(userReq.getRequiredToken(), workspaceID);
    } catch (SamApiException ex) {
      // Do nothing if the resource to delete is not found, this may not be the first time undo is
      // called. Other exceptions still need to be surfaced.
      logger.debug(
          "Sam API error while undoing CreateWorkspaceAuthzStep, code is "
              + ex.getApiExceptionStatus());
      if (ex.getApiExceptionStatus() != HttpStatus.NOT_FOUND.value()) {
        throw ex;
      }
    }
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
