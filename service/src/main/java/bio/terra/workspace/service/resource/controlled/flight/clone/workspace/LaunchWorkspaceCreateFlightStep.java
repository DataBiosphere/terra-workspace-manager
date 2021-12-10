package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;

public class LaunchWorkspaceCreateFlightStep implements Step {

  public LaunchWorkspaceCreateFlightStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final FlightMap workingMap = context.getWorkingMap();
    final FlightMap inputMap = context.getInputParameters();

    FlightUtils.validateCommonEntries(inputMap);
    FlightUtils.validateRequiredEntries(
        workingMap, ControlledResourceKeys.WORKSPACE_CREATE_FLIGHT_ID);

    final var workspaceCreateJobId =
        workingMap.get(ControlledResourceKeys.WORKSPACE_CREATE_FLIGHT_ID, String.class);

    // build input parameter map for subflight. Some entries are directly copied from
    // this flight's input parameters.
    final FlightMap subflightParameters = new FlightMap();
    FlightUtils.copyCommonParams(inputMap, subflightParameters);

    try {
      context
          .getStairway()
          .submit(workspaceCreateJobId, WorkspaceCreateFlight.class, subflightParameters);
    } catch (DuplicateFlightIdException ignored) {
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
