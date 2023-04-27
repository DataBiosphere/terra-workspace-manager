package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import java.util.UUID;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class CreateDbGcpCloudContextStartStep implements Step {
  private final UUID workspaceUuid;
  private final GcpCloudContextService gcpCloudContextService;

  public CreateDbGcpCloudContextStartStep(
      UUID workspaceUuid, GcpCloudContextService gcpCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    try {
      gcpCloudContextService.createGcpCloudContextStart(workspaceUuid, flightContext.getFlightId());
    } catch (DuplicateCloudContextException e) {
      // On a retry or restart, we may have already started the cloud context create
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one we created
    gcpCloudContextService.deleteGcpCloudContextWithCheck(
        workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
