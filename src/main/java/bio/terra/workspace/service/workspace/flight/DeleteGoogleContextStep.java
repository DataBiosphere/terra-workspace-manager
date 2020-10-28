package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;

import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** Deletes a workspace's Google cloud context. */
public class DeleteGoogleContextStep implements Step {
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
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);

    // Update the cloud context within a transaction so that we don't clobber a concurrent cloud
    // context change.
    return transactionTemplate.execute(
        status -> {
          WorkspaceCloudContext existingContext = workspaceDao.getCloudContext(workspaceId);
          if (existingContext.googleProjectId().isEmpty()) {
            // Restore the google cloud context if there's nothing there now.
            workspaceDao.updateCloudContext(
                workspaceId, WorkspaceCloudContext.createGoogleContext(projectId));
            return StepResult.getStepResultSuccess();
          } else if (existingContext.googleProjectId().get().equals(projectId)) {
            // If the project id is still there, there's nothing to undo.
            return StepResult.getStepResultSuccess();
          } else {
            // If a different project id is now in the cloud context, panic. We can no longer undo
            // deletion.
            return new StepResult(
                StepStatus.STEP_RESULT_FAILURE_FATAL,
                new IllegalStateException(
                    String.format(
                        "Project id to undo delete [%s] does not match existing project id [%s]",
                        projectId, existingContext.googleProjectId().get())));
          }
        });
  }
}
