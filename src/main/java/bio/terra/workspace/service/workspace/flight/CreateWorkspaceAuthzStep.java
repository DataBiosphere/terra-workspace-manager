package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateWorkspaceAuthzStep implements Step {

  private SamService samService;
  private AuthenticatedUserRequest userReq;

  private final Logger logger = LoggerFactory.getLogger(CreateWorkspaceAuthzStep.class);

  public CreateWorkspaceAuthzStep(SamService samService, AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.userReq = userReq;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);

    // This may seem a bit counterintuitive, but in many of the existing use-cases, the workspace
    // resource already exists
    // If the user has access to the workspace, it means that we can skip the case of trying (and
    // failing) to create
    // the Sam resource. If the user doesn't have access, we'll default to the existing behavior and
    // return the following
    // error or success message from Sam.
    if (!samService.isAuthorized(
        userReq.getRequiredToken(),
        SamUtils.SAM_WORKSPACE_RESOURCE,
        workspaceID.toString(),
        SamUtils.SAM_WORKSPACE_READ_ACTION)) {
      samService.createWorkspaceWithDefaults(userReq.getRequiredToken(), workspaceID);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    // Only delete the Sam resource if we actually created it in the do step.
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    try {
      samService.deleteWorkspace(userReq.getRequiredToken(), workspaceID);
    } catch (SamApiException ex) {
      // Do nothing if the resource to delete is not found, this may not be the first time undo is
      // called. Other exceptions still need to be surfaced.
      logger.debug(
          "Sam API error while undoing CreateWorkspaceAuthzStep, code is "
              + ex.getApiExceptionStatus());
      if (ex.getApiExceptionStatus() != 404) {
        throw ex;
      }
    }
    return StepResult.getStepResultSuccess();
  }
}
