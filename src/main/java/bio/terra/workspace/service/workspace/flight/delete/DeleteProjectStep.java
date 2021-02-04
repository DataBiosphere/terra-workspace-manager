package bio.terra.workspace.service.workspace.flight.delete;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.flight.GoogleUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start deletion of a Google Project.
 *
 * <p>Note that GCP does not delete projects entirely immediately: they go into a "deleting" state
 * where they exist for up to 30 days.
 *
 * <p>Undo always fails for this step.
 */
public class DeleteProjectStep implements Step {
  private final CloudResourceManagerCow resourceManager;
  private final WorkspaceDao workspaceDao;
  private final Logger logger = LoggerFactory.getLogger(DeleteProjectStep.class);

  public DeleteProjectStep(CloudResourceManagerCow resourceManager, WorkspaceDao workspaceDao) {
    this.resourceManager = resourceManager;
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    Optional<String> projectId = workspaceDao.getCloudContext(workspaceId).googleProjectId();
    if (projectId.isEmpty()) {
      // Nothing to delete.
      return StepResult.getStepResultSuccess();
    }
    try {
      GoogleUtils.deleteProject(projectId.get(), resourceManager);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    Optional<String> projectId = workspaceDao.getCloudContext(workspaceId).googleProjectId();
    if (projectId.isEmpty()) {
      // Nothing to delete, so nothing to undo.
      return StepResult.getStepResultSuccess();
    }
    // Do not attempt to undo project deletions.
    // TODO: investigate recovering projects if this happens often and recovery works as a "undo."
    logger.error("Unable to undo deletion of project [{}]", projectId);
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
