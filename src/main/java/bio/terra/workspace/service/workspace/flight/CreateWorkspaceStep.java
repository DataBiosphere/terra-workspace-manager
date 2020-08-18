package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.MDCUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

public class CreateWorkspaceStep implements Step {

  private WorkspaceDao workspaceDao;
  private MDCUtils mdcUtils;

  private static final String CREATE_WORKSPACE_COMPLETED_KEY = "createWorkspaceStepCompleted";

  public CreateWorkspaceStep(WorkspaceDao workspaceDao, MDCUtils mdcUtils) {
    this.workspaceDao = workspaceDao;
    this.mdcUtils = mdcUtils;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();

    String serializedMdc = inputMap.get(WorkspaceFlightMapKeys.MDC_KEY, String.class);
    MDC.setContextMap(mdcUtils.deserializeMdcString(serializedMdc));

    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(CREATE_WORKSPACE_COMPLETED_KEY, false);

    // This can be null if no spend profile is specified
    UUID nullableSpendProfileId = null;

    UUID spendProfileId = inputMap.get(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, UUID.class);
    if (spendProfileId != null) {
      nullableSpendProfileId = spendProfileId;
    }

    workspaceDao.createWorkspace(workspaceId, nullableSpendProfileId);
    workingMap.put(CREATE_WORKSPACE_COMPLETED_KEY, true);

    CreatedWorkspace response = new CreatedWorkspace();
    response.setId(workspaceId);
    FlightUtils.setResponse(flightContext, response, HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
    String serializedMdc = inputMap.get(WorkspaceFlightMapKeys.MDC_KEY, String.class);
    MDC.setContextMap(mdcUtils.deserializeMdcString(serializedMdc));
    if (workingMap.get(CREATE_WORKSPACE_COMPLETED_KEY, Boolean.class)) {
      UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
      workspaceDao.deleteWorkspace(workspaceId);
    }
    return StepResult.getStepResultSuccess();
  }
}
