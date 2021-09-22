package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class StoreGcpContextStep implements Step {
  private final WorkspaceDao workspaceDao;
  private final UUID workspaceId;

  public StoreGcpContextStep(WorkspaceDao workspaceDao, UUID workspaceId) {
    this.workspaceDao = workspaceDao;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    String projectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);

    // Create the cloud context; throws if the context already exists. We let
    // Stairway handle that.
    workspaceDao.createGcpCloudContext(
        workspaceId, new GcpCloudContext(projectId), flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one with our flight id
    workspaceDao.deleteGcpCloudContextWithCheck(workspaceId, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
