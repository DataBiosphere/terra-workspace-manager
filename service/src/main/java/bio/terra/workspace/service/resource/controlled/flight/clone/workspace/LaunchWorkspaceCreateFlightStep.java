package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;

public class LaunchWorkspaceCreateFlightStep implements Step {

  public LaunchWorkspaceCreateFlightStep() {
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final FlightMap workingMap = context.getWorkingMap();
    FlightUtils.validateRequiredEntriesNonNull(workingMap,
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        ControlledResourceKeys.WORKSPACE_CREATE_JOB_ID);

    // build input parameter map for subflight
    final FlightMap subflightInputParameters = new FlightMap();

    return null;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return null;
  }
}
