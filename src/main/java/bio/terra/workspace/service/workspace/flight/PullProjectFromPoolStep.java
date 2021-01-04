package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.buffer.model.HandoutRequestBody;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.buffer.model.ResourceInfo;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.buffer.BufferService;
import java.util.UUID;

// NOTE: Do not use. This is just a test step to exercise RBS connection.
public class PullProjectFromPoolStep implements Step {
  private final BufferService bufferService;

  public PullProjectFromPoolStep(BufferService bufferService) {
    this.bufferService = bufferService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    PoolInfo poolInfo = bufferService.getPoolInfo();

    // TODO(tovanadler): Move to it's own stairway step so that we can undo.
    String resourceId = UUID.randomUUID().toString();
    HandoutRequestBody body = new HandoutRequestBody();
    body.setHandoutRequestId(resourceId);

    ResourceInfo info = bufferService.handoutResource(body);
    flightContext
        .getWorkingMap()
        .put(GOOGLE_PROJECT_ID, info.getCloudResourceUid().getGoogleProjectUid().getProjectId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // TODO - Figure out what needs to be cleaned up.
    return StepResult.getStepResultSuccess();
  }
}
