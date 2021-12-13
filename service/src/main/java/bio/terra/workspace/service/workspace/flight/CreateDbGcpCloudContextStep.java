package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import java.util.UUID;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class CreateDbGcpCloudContextStep implements Step {
  private final UUID workspaceId;
  private final GcpCloudContextService gcpCloudContextService;

  public CreateDbGcpCloudContextStep(
      UUID workspaceId, GcpCloudContextService gcpCloudContextService) {
    this.workspaceId = workspaceId;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    gcpCloudContextService.createGcpCloudContextStart(workspaceId, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one we created
    gcpCloudContextService.deleteGcpCloudContextWithCheck(workspaceId, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
