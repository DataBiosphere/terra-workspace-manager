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

  public StoreGcpContextStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String projectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);

    // Create the cloud context; throws if the context already exists. We let
    // Stairway handle that.
    workspaceDao.createGcpCloudContext(workspaceId, new GcpCloudContext(projectId));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String projectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);

    // Delete the cloud context, but only if it is the one with our project id
    workspaceDao.deleteGcpCloudContextWithIdCheck(workspaceId, projectId);
    return StepResult.getStepResultSuccess();
  }
}
