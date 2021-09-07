package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdSubmittedException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import javax.annotation.Nullable;

public class LaunchWorkspaceCreateFlightStep implements Step {

  public LaunchWorkspaceCreateFlightStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final FlightMap workingMap = context.getWorkingMap();
    FlightUtils.validateRequiredEntries(
        context.getInputParameters(),
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        WorkspaceFlightMapKeys.SPEND_PROFILE_ID);

    FlightUtils.validateRequiredEntries(
        workingMap,
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        ControlledResourceKeys.WORKSPACE_CREATE_FLIGHT_ID);

    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    @Nullable
    final var description =
        context.getInputParameters().get(WorkspaceFlightMapKeys.DESCRIPTION, String.class);
    @Nullable
    final var displayName =
        context.getInputParameters().get(WorkspaceFlightMapKeys.DISPLAY_NAME, String.class);
    final var spendProfileId =
        context.getInputParameters().get(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, String.class);
    final var workspaceCreateJobId =
        workingMap.get(ControlledResourceKeys.WORKSPACE_CREATE_FLIGHT_ID, String.class);

    final var destinationWorkspaceId =
        context.getWorkingMap().get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    // build input parameter map for subflight. Some entries are directly copied from
    // this flight's input parameters.
    final FlightMap subflightInputParameters = new FlightMap();
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(WorkspaceFlightMapKeys.DESCRIPTION, description);
    subflightInputParameters.put(WorkspaceFlightMapKeys.DISPLAY_NAME, displayName);
    subflightInputParameters.put(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, spendProfileId);
    subflightInputParameters.put(WorkspaceFlightMapKeys.WORKSPACE_ID, destinationWorkspaceId);
    subflightInputParameters.put(
        WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.MC_WORKSPACE.toString());

    try {
      context
          .getStairway()
          .submit(workspaceCreateJobId, WorkspaceCreateFlight.class, subflightInputParameters);
    } catch (DuplicateFlightIdSubmittedException ignored) {
      // this is a rerun, so it's benign
      return StepResult.getStepResultSuccess();
    } catch (DatabaseOperationException | StairwayExecutionException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  // There is no way to kill a running flight currently, so nothing else useful to
  // undo here.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
