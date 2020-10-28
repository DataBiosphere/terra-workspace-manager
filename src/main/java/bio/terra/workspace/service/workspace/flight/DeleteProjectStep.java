package bio.terra.workspace.service.workspace.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Start deletion of a Google Project.
 * <p>Note that GCP does not delete projects entirely immediately: they go into a "deleting" state where they exist for up to 30 days.
 * <p> Undo always fails for this step.*/
public class DeleteProjectStep implements Step {
  private final CloudResourceManagerCow resourceManager;
  private Logger logger = LoggerFactory.getLogger(DeleteProjectStep.class);

  public DeleteProjectStep(CloudResourceManagerCow resourceManager) {
    this.resourceManager = resourceManager;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
      String projectId = flightContext.getInputParameters().get(WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID, String.class);
      try {
          GoogleUtils.deleteProject(projectId, resourceManager);
      } catch (IOException e) {
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
      }
      return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
      // Do not attempt to undo project deletions.
      // TODO: investigate recovering projects if this happens often and recovery works as a "undo."
      String projectId = flightContext.getInputParameters().get(WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID, String.class);
      logger.error("Unable to undo deletion of project [{}]", projectId);
    return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
