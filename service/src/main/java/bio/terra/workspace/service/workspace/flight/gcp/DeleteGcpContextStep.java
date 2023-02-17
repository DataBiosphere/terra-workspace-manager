package bio.terra.workspace.service.workspace.flight.gcp;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deletes a workspace's Google cloud context from the DAO. */
public class DeleteGcpContextStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(DeleteGcpContextStep.class);
  private final GcpCloudContextService gcpCloudContextService;
  private final UUID workspaceUuid;

  protected DeleteGcpContextStep(
      GcpCloudContextService gcpCloudContextService, UUID workspaceUuid) {
    this.gcpCloudContextService = gcpCloudContextService;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    gcpCloudContextService.deleteGcpCloudContext(workspaceUuid);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Right now, we don't attempt to undo DAO deletion. This is expected to happen infrequently and
    // not before steps that are likely to fail.
    logger.error("Unable to undo DAO deletion of google context [{}]", workspaceUuid);
    return flightContext.getResult();
  }
}
