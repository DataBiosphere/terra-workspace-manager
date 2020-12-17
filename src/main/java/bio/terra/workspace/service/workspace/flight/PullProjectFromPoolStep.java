package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.buffer.model.HandoutRequestBody;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.buffer.model.ResourceInfo;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.service.buffer.BufferService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullProjectFromPoolStep implements Step {

  private final BufferService bufferService;

  private final Logger logger = LoggerFactory.getLogger(PullProjectFromPoolStep.class);

  public PullProjectFromPoolStep(BufferService bufferService) {
    this.bufferService = bufferService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    PoolInfo poolInfo = bufferService.getPoolInfo();
    if (poolInfo == null) {
      // TODO - this should be a different step.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    // TODO - Do we want to re-use resuorce id?
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
    // TODO - Figure out what needs to be cleaned up/
    return StepResult.getStepResultSuccess();
  }
}
