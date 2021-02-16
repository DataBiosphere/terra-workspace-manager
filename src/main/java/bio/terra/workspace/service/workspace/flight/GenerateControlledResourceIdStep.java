package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import java.util.UUID;

public class GenerateControlledResourceIdStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    flightContext
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_ID, UUID.randomUUID());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    flightContext.getWorkingMap().put(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_ID, null);
    return StepResult.getStepResultSuccess();
  }
}
