package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.MDCUtils;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import java.util.UUID;
import org.slf4j.MDC;

public class CreateWorkspaceAuthzStep implements Step {

  private SamService samService;
  private AuthenticatedUserRequest userReq;
  private MDCUtils mdcUtils;

  private static final String AUTHZ_COMPLETED_KEY = "createWorkspaceAuthzStepCompleted";

  public CreateWorkspaceAuthzStep(
      SamService samService, AuthenticatedUserRequest userReq, MDCUtils mdcUtils) {
    this.samService = samService;
    this.userReq = userReq;
    this.mdcUtils = mdcUtils;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    String serializedMdc = inputMap.get(WorkspaceFlightMapKeys.MDC_KEY, String.class);
    MDC.setContextMap(mdcUtils.deserializeMdcString(serializedMdc));
    UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(AUTHZ_COMPLETED_KEY, false);

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
    String serializedMdc = inputMap.get(WorkspaceFlightMapKeys.MDC_KEY, String.class);
    MDC.setContextMap(mdcUtils.deserializeMdcString(serializedMdc));
    // Only delete the Sam resource if we actually created it in the do step.
    FlightMap workingMap = flightContext.getWorkingMap();
    if (workingMap.get(AUTHZ_COMPLETED_KEY, Boolean.class)) {
      UUID workspaceID = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
      samService.deleteWorkspace(userReq.getRequiredToken(), workspaceID);
    }
    return StepResult.getStepResultSuccess();
  }
}
