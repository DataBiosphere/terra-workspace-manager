package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntriesNonNull;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

public class LaunchCreateGcpContextFlightStep implements Step {

  private final WorkspaceService workspaceService;

  public LaunchCreateGcpContextFlightStep(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntriesNonNull(
        context.getInputParameters(),
        ControlledResourceKeys.SOURCE_WORKSPACE_ID,
        JobMapKeys.AUTH_USER_INFO.getKeyName());
    validateRequiredEntriesNonNull(
        context.getWorkingMap(),
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        ControlledResourceKeys.CREATE_CLOUD_CONTEXT_JOB_ID);

    final var sourceWorkspaceId =
        context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final var destinationWorkspaceId =
        context.getWorkingMap().get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    final var cloudContextJobId =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.CREATE_CLOUD_CONTEXT_JOB_ID, String.class);

    boolean flightAlreadyExists;
    try {
      final FlightState flightState = context.getStairway().getFlightState(cloudContextJobId);
      flightAlreadyExists = true;
    } catch (FlightNotFoundException e) {
      flightAlreadyExists = false;
    } catch (DatabaseOperationException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    // we already have a flight, so don't launch another one
    if (!flightAlreadyExists) {
      workspaceService.createGcpCloudContext(
          destinationWorkspaceId, cloudContextJobId, userRequest);
    }
    return StepResult.getStepResultSuccess();
  }

  /** Destroy the created workspace and cloud context */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final var destinationWorkspaceId =
        context.getWorkingMap().get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    if (destinationWorkspaceId != null && userRequest != null) {
      workspaceService.deleteWorkspace(destinationWorkspaceId, userRequest);
    } // otherwise, if it never got created, that's fine too
    return StepResult.getStepResultSuccess();
  }
}
