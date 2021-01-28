package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import java.util.Optional;
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
    WorkspaceCloudContext cloudContext =
        WorkspaceCloudContext.builder().googleProjectId(Optional.of(projectId)).build();
    FlightUtils.setResponse(flightContext, cloudContext, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
