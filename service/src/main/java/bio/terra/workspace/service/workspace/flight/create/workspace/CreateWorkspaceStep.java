package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class CreateWorkspaceStep implements Step {

  private final WorkspaceDao workspaceDao;
  private final Workspace workspace;
  private final List<String> applicationIds;

  private final Logger logger = LoggerFactory.getLogger(CreateWorkspaceStep.class);

  public CreateWorkspaceStep(
      Workspace workspace, List<String> applicationIds, WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
    this.workspace = workspace;
    this.applicationIds = applicationIds;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws RetryException, InterruptedException {
    UUID workspaceUuid = workspace.getWorkspaceId();

    try {
      workspaceDao.createWorkspace(workspace, applicationIds);
    } catch (DuplicateWorkspaceException ex) {
      // This might be the result of a step re-running, or it might be an ID conflict. We can ignore
      // this if the existing workspace matches the one we were about to create, otherwise rethrow.
      Workspace existingWorkspace = workspaceDao.getWorkspace(workspaceUuid);
      if (!workspace.equals(existingWorkspace)) {
        throw ex;
      }
    }

    FlightUtils.setResponse(flightContext, workspaceUuid, HttpStatus.OK);
    logger.info("Workspace created with id {}", workspaceUuid);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    UUID workspaceUuid = workspace.getWorkspaceId();
    // Ignore return value, as we don't care whether a workspace was deleted or just not found.
    workspaceDao.deleteWorkspace(workspaceUuid);
    return StepResult.getStepResultSuccess();
  }
}
