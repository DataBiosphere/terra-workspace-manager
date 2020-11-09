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
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CreateWorkspaceStep implements Step {

  private WorkspaceDao workspaceDao;

  private static final String CREATE_WORKSPACE_COMPLETED_KEY = "createWorkspaceStepCompleted";

  public CreateWorkspaceStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();

    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(CREATE_WORKSPACE_COMPLETED_KEY, false);

    Optional<SpendProfileId> spendProfileId =
        Optional.ofNullable(inputMap.get(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, String.class))
            .map(SpendProfileId::create);
    WorkspaceStage workspaceStage =
        inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.class);

    workspaceDao.createWorkspace(workspaceId, spendProfileId, workspaceStage);
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
    if (workingMap.get(CREATE_WORKSPACE_COMPLETED_KEY, Boolean.class)) {
      UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
      workspaceDao.deleteWorkspace(workspaceId);
    }
    return StepResult.getStepResultSuccess();
  }
}
