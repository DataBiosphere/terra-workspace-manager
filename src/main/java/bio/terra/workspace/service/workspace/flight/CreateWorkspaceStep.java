package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class CreateWorkspaceStep implements Step {

  private final WorkspaceDao workspaceDao;

  private final Logger logger = LoggerFactory.getLogger(CreateWorkspaceStep.class);

  public CreateWorkspaceStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();

    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);

    Optional<SpendProfileId> spendProfileId =
        Optional.ofNullable(inputMap.get(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, String.class))
            .map(SpendProfileId::create);
    WorkspaceStage workspaceStage =
        inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.class);
    boolean isSamResourceOwner =
        inputMap.get(WorkspaceFlightMapKeys.IS_SAM_RESOURCE_OWNER, Boolean.class);
    Workspace workspaceToCreate =
        Workspace.builder()
            .workspaceId(workspaceId)
            .spendProfileId(spendProfileId)
            .workspaceStage(workspaceStage)
            .isSamResourceOwner(isSamResourceOwner)
            .build();

    workspaceDao.createWorkspace(workspaceToCreate);

    FlightUtils.setResponse(flightContext, workspaceId, HttpStatus.OK);
    logger.info("Workspace created with id {}", workspaceId);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    // Ignore return value, as we don't care whether a workspace was deleted or just not found.
    workspaceDao.deleteWorkspace(workspaceId);
    return StepResult.getStepResultSuccess();
  }
}
