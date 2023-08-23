package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;

public class LaunchCreateCloudContextFlightStep implements Step {

  private final WorkspaceService workspaceService;
  private final CloudPlatform cloudPlatform;
  private final String flightIdKey;
  private final SpendProfile spendProfile;

  public LaunchCreateCloudContextFlightStep(
      WorkspaceService workspaceService,
      CloudPlatform cloudPlatform,
      SpendProfile spendProfile,
      String flightIdKey) {
    this.workspaceService = workspaceService;
    this.cloudPlatform = cloudPlatform;
    this.flightIdKey = flightIdKey;
    this.spendProfile = spendProfile;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(), ControlledResourceKeys.SOURCE_WORKSPACE_ID);
    validateRequiredEntries(context.getWorkingMap(), flightIdKey);

    FlightMap inputs = context.getInputParameters();

    var userRequest =
        FlightUtils.getRequired(
            inputs, JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspace =
        FlightUtils.getRequired(inputs, JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    var cloudContextJobId =
        FlightUtils.getRequired(context.getWorkingMap(), flightIdKey, String.class);

    boolean flightAlreadyExists;
    try {
      context.getStairway().getFlightState(cloudContextJobId);
      flightAlreadyExists = true;
    } catch (FlightNotFoundException e) {
      flightAlreadyExists = false;
    } catch (DatabaseOperationException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    // if we already have a flight, don't launch another one
    if (!flightAlreadyExists) {
      workspaceService.createCloudContext(
          destinationWorkspace, cloudPlatform, spendProfile, cloudContextJobId, userRequest, null);
    }
    return StepResult.getStepResultSuccess();
  }

  /** Nested flights should clean up their own messes. */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
