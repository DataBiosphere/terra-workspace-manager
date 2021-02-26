package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CLOUD_CONTEXT_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.model.CloudType;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class StoreGoogleContextStep implements Step {
  private final WorkspaceDao workspaceDao;

  public StoreGoogleContextStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    UUID cloudContextId = flightContext.getWorkingMap().get(CLOUD_CONTEXT_ID, UUID.class);

    // Create the cloud context; throws if the context already exists. We let
    // Stairway handle that.
    workspaceDao.createCloudContext(workspaceId, new GcpCloudContext(projectId, cloudContextId));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    UUID cloudContextId = flightContext.getWorkingMap().get(CLOUD_CONTEXT_ID, UUID.class);

    // Delete the cloud context, but only if it is the one with our id
    workspaceDao.deleteCloudContextByContextId(workspaceId, CloudType.GCP, cloudContextId);
    return StepResult.getStepResultSuccess();
  }
}
