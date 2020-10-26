package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class StoreGoogleContextStep implements Step {
  private final WorkspaceDao workspaceDao;
  private final TransactionTemplate transactionTemplate;

  protected StoreGoogleContextStep(
      WorkspaceDao workspaceDao, TransactionTemplate transactionTemplate) {
    this.workspaceDao = workspaceDao;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);

    return transactionTemplate.execute(
        (status -> {
          WorkspaceCloudContext cloudContext = workspaceDao.getCloudContext(workspaceId);
          if (cloudContext.googleProjectId().isPresent()) {
            String existingProjectId = cloudContext.googleProjectId().get();
            if (!existingProjectId.equals(projectId)) {
              return new StepResult(
                  StepStatus.STEP_RESULT_FAILURE_FATAL,
                  new IllegalStateException(
                      String.format(
                          "Created project id [%s] does not match existing project id [%s]",
                          projectId, existingProjectId)));
            }
            return StepResult.getStepResultSuccess();
          }
          workspaceDao.updateCloudContext(
              workspaceId, WorkspaceCloudContext.createGoogleContext(projectId));
          return StepResult.getStepResultSuccess();
        }));
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);

    transactionTemplate.execute(
        status -> {
          WorkspaceCloudContext cloudContext = workspaceDao.getCloudContext(workspaceId);
          if (cloudContext.googleProjectId().get().equals(projectId)) {
            // TODO: once multiple clouds are supported, we need to only clear the google context if
            // it
            // exists.
            workspaceDao.updateCloudContext(workspaceId, WorkspaceCloudContext.none());
          }
          return null;
        });
    return StepResult.getStepResultSuccess();
  }
}
