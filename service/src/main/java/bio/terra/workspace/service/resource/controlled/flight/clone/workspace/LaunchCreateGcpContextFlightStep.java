package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.FlightContext;
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
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;

public class LaunchCreateGcpContextFlightStep implements Step {

  private final WorkspaceService workspaceService;

  public LaunchCreateGcpContextFlightStep(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        ControlledResourceKeys.SOURCE_WORKSPACE_ID,
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        JobMapKeys.REQUEST.getKeyName());
    validateRequiredEntries(
        context.getWorkingMap(), ControlledResourceKeys.CREATE_CLOUD_CONTEXT_FLIGHT_ID);

    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);

    final var cloudContextJobId =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.CREATE_CLOUD_CONTEXT_FLIGHT_ID, String.class);

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
      workspaceService.createGcpCloudContext(
          destinationWorkspace.getWorkspaceId(), cloudContextJobId, userRequest);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Destroy the created workspace and cloud context. The only time we want to run the workspace
   * delete is if the create workspace subflight succeeded, but a later step in the flight fails.
   * The failure of the create workspace flight will have deleted the workspace on undo and we don't
   * get here. But if we fail to create the flight context, we want to delete the workspace.
   */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final var destinationWorkspaceId =
        context.getWorkingMap().get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    if (destinationWorkspaceId != null && userRequest != null) {
      // delete workspace is idempotent, so it's safe to call it more than once
      workspaceService.deleteWorkspace(destinationWorkspaceId, userRequest);
    } // otherwise, if it never got created, that's fine too
    return StepResult.getStepResultSuccess();
  }
}
