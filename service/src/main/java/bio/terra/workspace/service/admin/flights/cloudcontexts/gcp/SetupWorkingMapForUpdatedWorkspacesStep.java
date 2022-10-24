package bio.terra.workspace.service.admin.flights.cloudcontexts.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.UPDATED_WORKSPACES;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import java.util.HashSet;

/**
 * Add an empty hashset to the working map that is to put all the updated workspace id in. This is
 * for activity logging to record all the updated workspaces.
 */
public class SetupWorkingMapForUpdatedWorkspacesStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    context.getWorkingMap().put(UPDATED_WORKSPACES, new HashSet<String>());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
