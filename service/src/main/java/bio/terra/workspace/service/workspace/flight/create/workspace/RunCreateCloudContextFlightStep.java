package bio.terra.workspace.service.workspace.flight.create.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.exception.DuplicateJobIdException;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunCreateCloudContextFlightStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RunCreateCloudContextFlightStep.class);
  private static final Duration TOTAL_DURATION = Duration.ofHours(1);
  private static final Duration INITIAL_SLEEP = Duration.ofSeconds(10);
  private static final double FACTOR_INCREASE = 0.7;
  private static final Duration MAX_SLEEP = Duration.ofMinutes(2);

  private final CloudPlatform cloudPlatform;
  private final String flightId;
  private final SpendProfile spendProfile;
  private final AuthenticatedUserRequest userRequest;
  private final Workspace workspace;
  private final WorkspaceService workspaceService;

  public RunCreateCloudContextFlightStep(
      WorkspaceService workspaceService,
      Workspace workspace,
      CloudPlatform cloudPlatform,
      SpendProfile spendProfile,
      String flightId,
      AuthenticatedUserRequest userRequest) {
    this.cloudPlatform = cloudPlatform;
    this.flightId = flightId;
    this.spendProfile = spendProfile;
    this.userRequest = userRequest;
    this.workspace = workspace;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Stairway stairway = context.getStairway();

    try {
      workspaceService.createCloudContext(
          workspace, cloudPlatform, spendProfile, flightId, userRequest, null);
      logger.info(
          "Launched create {} cloud context for workspace {} in flight {}",
          cloudPlatform,
          workspace.workspaceId(),
          flightId);
    } catch (DuplicateJobIdException | DuplicateFlightIdException e) {
      // We will see duplicate id on a retry. Quietly continue.
    }

    return FlightUtils.waitForSubflightCompletion(stairway, flightId).convertToStepResult();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // We cannot undo the flight, but it undoes itself, so no undoing here.
    return StepResult.getStepResultSuccess();
  }
}
