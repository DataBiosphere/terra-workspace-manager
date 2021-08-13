package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import java.util.Optional;
import java.util.UUID;

public class CreateDestinationCloudContextStep implements Step {

  private final WorkspaceService workspaceService;

  public CreateDestinationCloudContextStep(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final var sourceWorkspaceId =
        context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final var destinationWorkspaceId =
        context.getWorkingMap().get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final Workspace sourceWorkspace = workspaceService.getWorkspace(sourceWorkspaceId, userRequest);

    // Chack if hte destination workspace already exists

    final var workspaceRequest =
        WorkspaceRequest.builder()
            .workspaceId(destinationWorkspaceId)
            .displayName(sourceWorkspace.getDisplayName().map(n -> n + " (clone)"))
            .description(Optional.of(String.format("Clone of workspace ID %s", sourceWorkspaceId)))
            .build();
    // TODO: harden this to avoid creating a duplicate workspace after a restart. May need to pass
    //   in an ID instead of having it instantiated inside the method.
    workspaceService.createWorkspace(workspaceRequest, userRequest);

    final var cloudContextJobId =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.CREATE_CLOUD_CONTEXT_JOB_ID, String.class);

    boolean flightAlreadyExists;
    try {
      final FlightState flightState = context.getStairway().getFlightState(cloudContextJobId);
      flightAlreadyExists = true;
    } catch (DatabaseOperationException e) {
      flightAlreadyExists = false;
    }
    try {
      // we already have a flight, so don't launch another one
      if (!flightAlreadyExists) {
        workspaceService.createGcpCloudContext(
            destinationWorkspaceId, cloudContextJobId, userRequest);
      }
      context.getStairway().waitForFlight(cloudContextJobId, 10, 100);

    } catch (DatabaseOperationException | FlightException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
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
