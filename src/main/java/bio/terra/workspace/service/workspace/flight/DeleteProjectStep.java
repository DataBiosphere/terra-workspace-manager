package bio.terra.workspace.service.workspace.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
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
  private final CrlService crl;
  private final WorkspaceDao workspaceDao;
  private final Logger logger = LoggerFactory.getLogger(DeleteProjectStep.class);

  public DeleteProjectStep(CrlService crl, WorkspaceDao workspaceDao) {
    this.crl = crl;
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    Optional<GcpCloudContext> cloudContext = getContext(flightContext);
    if (cloudContext.isPresent()) {
      CloudResourceManagerCow resourceManager = crl.getCloudResourceManagerCow();
      try {
        String projectId = cloudContext.get().getGcpProjectId();
        GcpUtils.deleteProject(projectId, resourceManager);
      } catch (IOException e) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    Optional<GcpCloudContext> cloudContext = getContext(flightContext);
    if (cloudContext.isEmpty()) {
      // Nothing was deleted, so nothing to undo.
      return StepResult.getStepResultSuccess();
    }
    // Do not attempt to undo project deletions.
    // TODO: investigate recovering projects if this happens often and recovery works as a "undo."
    String projectId = cloudContext.get().getGcpProjectId();
    logger.error("Unable to undo deletion of project [{}]", projectId);
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }

  private Optional<GcpCloudContext> getContext(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    return workspaceDao.getGcpCloudContext(workspaceId);
  }
}
