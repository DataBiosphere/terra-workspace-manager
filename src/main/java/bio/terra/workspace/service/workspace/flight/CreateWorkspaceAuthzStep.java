package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateWorkspaceAuthzStep implements Step {

  private SamService samService;
  private AuthenticatedUserRequest userReq;

  private static final String AUTHZ_COMPLETED_KEY = "createWorkspaceAuthzStepCompleted";

  private static Logger logger = LoggerFactory.getLogger(CreateWorkspaceAuthzStep.class);

  public CreateWorkspaceAuthzStep(SamService samService, AuthenticatedUserRequest userReq) {
    this.samService = samService;
    this.userReq = userReq;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    FlightMap workingMap = flightContext.getWorkingMap();

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
      workingMap.put(AUTHZ_COMPLETED_KEY, true);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    // Only delete the Sam resource if we actually created it in the do step.
    FlightMap workingMap = flightContext.getWorkingMap();
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    if (workingMap.get(AUTHZ_COMPLETED_KEY, Boolean.class) != null) {
      samService.deleteWorkspace(userReq.getRequiredToken(), workspaceID);
    } else {
      logger.warn(
          "Undoing a CreateWorkspaceAuthzStep that was not fully completed. This may have created an orphaned workspace "
              + workspaceID.toString()
              + " in Sam.");
    }
    return StepResult.getStepResultSuccess();
  }
}
