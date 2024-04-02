package bio.terra.workspace.service.workspace.flight.delete.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.delete.cloudcontext.DeleteCloudContextFlight;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunDeleteCloudContextFlightStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RunDeleteCloudContextFlightStep.class);
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

    return FlightUtils.waitForSubflightCompletion(stairway, flightId).convertToStepResult();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // We cannot undo the flight, but it undoes itself, so no undoing here.
    return StepResult.getStepResultSuccess();
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
