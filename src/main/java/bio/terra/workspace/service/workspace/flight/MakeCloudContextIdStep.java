package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import java.util.UUID;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class MakeCloudContextIdStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext) {
    UUID contextId = UUID.randomUUID();
    flightContext.getWorkingMap().put(WorkspaceFlightMapKeys.CLOUD_CONTEXT_ID, contextId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
