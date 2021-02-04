package bio.terra.workspace.service.workspace.flight.delete;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/** Deletes a workspace's Google cloud context from the DAO. */
public class DeleteGoogleContextStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteGoogleContextStep.class);
  private final WorkspaceDao workspaceDao;
  private final TransactionTemplate transactionTemplate;

  protected DeleteGoogleContextStep(
      WorkspaceDao workspaceDao, TransactionTemplate transactionTemplate) {
    this.workspaceDao = workspaceDao;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    // Update the cloud context within a transaction so that we don't clobber a concurrent cloud
    // context change.
    transactionTemplate.execute(
        status -> {
          Optional<String> projectId = workspaceDao.getCloudContext(workspaceId).googleProjectId();
          if (projectId.isPresent()) {
            workspaceDao.updateCloudContext(workspaceId, WorkspaceCloudContext.none());
          }
          return null;
        });
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
