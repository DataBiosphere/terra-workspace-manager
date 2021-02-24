package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

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
    // Note that for RAWLS_WORKSPACE stage workspaces, Rawls manages the Sam resource instead of
    // WSM.
    boolean isSamResourceOwner =
        inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.class)
            == WorkspaceStage.MC_WORKSPACE;
    if (isSamResourceOwner) {
      // Even though WSM should own this resource, Stairway steps can run multiple times, so it's
      // possible this step already created the resource. If WSM can either read the existing Sam
      // resource or create a new one, this is considered successful.
      if (!canReadExistingWorkspace(workspaceID)) {
        samService.createWorkspaceWithDefaults(userReq, workspaceID);
      }
      return StepResult.getStepResultSuccess();
    } else {
      // When WSM does not own the Sam resource, we still verify that the calling user has read
      // access.
      if (canReadExistingWorkspace(workspaceID)) {
        return StepResult.getStepResultSuccess();
      } else {
        throw new WorkspaceNotFoundException(
            String.format(
                "Could not find pre-existing Sam resource for workspace %s. WSM will not create Sam resources for RAWLS_WORKSPACE stage workspaces.",
                workspaceID));
      }
    }
  }

  private boolean canReadExistingWorkspace(UUID workspaceID) {
    return samService.isAuthorized(
        userReq.getRequiredToken(),
        SamConstants.SAM_WORKSPACE_RESOURCE,
        workspaceID.toString(),
        SamConstants.SAM_WORKSPACE_READ_ACTION);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    boolean isSamResourceOwner =
        inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.class)
            == WorkspaceStage.MC_WORKSPACE;
    // If WSM does not own this Sam resource, there's nothing to undo.
    if (!isSamResourceOwner) {
      return StepResult.getStepResultSuccess();
    }
    // If WSM does own this Sam resource, it should be deleted here.
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
}
