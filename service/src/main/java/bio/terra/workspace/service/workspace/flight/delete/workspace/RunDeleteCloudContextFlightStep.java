package bio.terra.workspace.service.workspace.flight.delete.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunDeleteCloudContextFlightStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RunDeleteCloudContextFlightStep.class);
  private static final Duration TOTAL_DURATION = Duration.ofHours(1);
  private static final Duration INITIAL_SLEEP = Duration.ofSeconds(10);
  private static final double FACTOR_INCREASE = 0.7;
  private static final Duration MAX_SLEEP = Duration.ofMinutes(2);

  private final UUID workspaceUuid;
  private final CloudPlatform cloudPlatform;
  private final AuthenticatedUserRequest userRequest;

  public RunDeleteCloudContextFlightStep(
      UUID workspaceUuid, CloudPlatform cloudPlatform, AuthenticatedUserRequest userRequest) {
    this.workspaceUuid = workspaceUuid;
    this.cloudPlatform = cloudPlatform;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputs.put(WorkspaceFlightMapKeys.CLOUD_PLATFORM, cloudPlatform);
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    Stairway stairway = context.getStairway();

    String flightId = getFlightId(context, cloudPlatform);

    try {
      stairway.submit(flightId, DeleteCloudContextFlight.class, inputs);
      logger.info(
          "Launched delete {} cloud context for workspace {} in flight {}",
          cloudPlatform,
          workspaceUuid,
          flightId);
    } catch (DuplicateFlightIdException e) {
      // We will see duplicate id on a retry. Quietly continue.
    }

    try {
      FlightState flightState =
          RetryUtils.getWithRetry(
              this::flightComplete,
              () -> stairway.getFlightState(flightId),
              TOTAL_DURATION,
              INITIAL_SLEEP,
              FACTOR_INCREASE,
              MAX_SLEEP);

      if (flightState.getFlightStatus() == FlightStatus.SUCCESS) {
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          flightState
              .getException()
              .orElse(
                  new RuntimeException(
                      "DeleteCloudContext flight failed with an empty exception")));

    } catch (Exception e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // We cannot undo the flight, but it undoes itself, so no undoing here.
    return StepResult.getStepResultSuccess();
  }

  private boolean flightComplete(FlightState flightState) {
    logger.info(
        "Delete {} cloud context state is {}", cloudPlatform, flightState.getFlightStatus());
    return (flightState.getFlightStatus() == FlightStatus.ERROR
        || flightState.getFlightStatus() == FlightStatus.FATAL
        || flightState.getFlightStatus() == FlightStatus.SUCCESS);
  }

  private String getFlightId(FlightContext context, CloudPlatform cloudPlatform) {
    Map<CloudPlatform, String> flightIds =
        FlightUtils.getRequired(
            context.getWorkingMap(), WorkspaceFlightMapKeys.FLIGHT_IDS, new TypeReference<>() {});

    String flightId = flightIds.get(cloudPlatform);
    if (flightId == null) {
      throw new InternalLogicException(
          "No flightId generated for cloud platform: " + cloudPlatform);
    }
    return flightId;
  }
}
