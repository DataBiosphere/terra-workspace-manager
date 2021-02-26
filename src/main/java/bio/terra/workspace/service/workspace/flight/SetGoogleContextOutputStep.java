package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.CLOUD_CONTEXT_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * A step which sets the output of the flight to the created google context and appropriate status
 * code.
 */
public class SetGoogleContextOutputStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    UUID contextId = flightContext.getWorkingMap().get(CLOUD_CONTEXT_ID, UUID.class);
    GcpCloudContext cloudContext = new GcpCloudContext(projectId, contextId);
    FlightUtils.setResponse(flightContext, cloudContext, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  // If the flight is undoing, another step has likely set an exception or error as the result.
  // To avoid clobbering that result, we do not attempt to clear the result in undo().
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
