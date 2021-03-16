package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.db.WorkspaceDao;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deletes a workspace's Google cloud context from the DAO. */
public class DeleteGcpContextStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteGcpContextStep.class);
  private final WorkspaceDao workspaceDao;

  protected DeleteGcpContextStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    workspaceDao.deleteGcpCloudContext(workspaceId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Right now, we don't attempt to undo DAO deletion. This is expected to happen infrequently and
    // not before steps that are likely to fail.
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    logger.error("Unable to undo DAO deletion of google context [{}]", workspaceId);
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
