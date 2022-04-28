package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
@Deprecated // TODO: PF-1238 remove
public class StoreGcpContextStep implements Step {
  private final GcpCloudContextService gcpCloudContextService;
  private final UUID workspaceUuid;

  public StoreGcpContextStep(GcpCloudContextService gcpCloudContextService, UUID workspaceUuid) {
    this.gcpCloudContextService = gcpCloudContextService;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    String projectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);

    // Create the cloud context; throws if another flight already created the context.
    Optional<String> creatingFlightId =
        gcpCloudContextService.getGcpCloudContextFlightId(workspaceUuid);
    if (creatingFlightId.isPresent()) {
      if (creatingFlightId.get().equals(flightContext.getFlightId())) {
        // This flight has already created the context, meaning this step is being re-run.
        return StepResult.getStepResultSuccess();
      } else {
        throw new DuplicateCloudContextException(
            String.format("Workspace %s already has a GCP cloud context", workspaceUuid));
      }
    }
    gcpCloudContextService.createGcpCloudContext(
        workspaceUuid, new GcpCloudContext(projectId), flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one with our flight id
    gcpCloudContextService.deleteGcpCloudContextWithCheck(
        workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
