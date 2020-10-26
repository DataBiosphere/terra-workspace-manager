package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.CloudType;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import java.util.UUID;

/** Stores the Google Project Id in the {@link WorkspaceDao}. */
public class StoreGoogleCloudContextStep implements Step {
  private final WorkspaceDao workspaceDao;

  public StoreGoogleCloudContextStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    if (workspaceDao.getCloudContext(workspaceId).cloudType().equals(CloudType.GOOGLE)) {
      // Already stored.
      return StepResult.getStepResultSuccess();
    }
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    workspaceDao.insertCloudContext(
        workspaceId, WorkspaceCloudContext.createGoogleContext(projectId));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    workspaceDao.deleteCloudContext(workspaceId);
    return StepResult.getStepResultSuccess();
  }
}
