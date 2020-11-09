package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.model.WorkspaceStage;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class CreateWorkspaceStep implements Step {

  private WorkspaceDao workspaceDao;

  private static final String CREATE_WORKSPACE_COMPLETED_KEY = "createWorkspaceStepCompleted";

  private static Logger logger = LoggerFactory.getLogger(CreateWorkspaceStep.class);

  public CreateWorkspaceStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();

    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    FlightMap workingMap = flightContext.getWorkingMap();

    // This can be null if no spend profile is specified
    UUID nullableSpendProfileId = null;

    UUID spendProfileId = inputMap.get(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, UUID.class);
    if (spendProfileId != null) {
      nullableSpendProfileId = spendProfileId;
    }

    WorkspaceStage workspaceStage =
        inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.class);

    workspaceDao.createWorkspace(workspaceId, nullableSpendProfileId, workspaceStage);
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
    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    if (workingMap.get(CREATE_WORKSPACE_COMPLETED_KEY, Boolean.class) != null) {
      workspaceDao.deleteWorkspace(workspaceId);
    } else {
      logger.warn(
          "Undoing a CreateWorkspaceStep that was not fully completed. This may have created an orphaned workspace "
              + workspaceId.toString()
              + " in WM's database.");
    }
    return StepResult.getStepResultSuccess();
  }
}
