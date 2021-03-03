package bio.terra.workspace.service.resource.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

import java.util.UUID;

/**
 * Stairway step to generate a unique id for a data reference.
 *
 * <p>Stairway's working map is only persisted at step boundaries. By generating an ID in its own
 * step, we ensure that both do and undo methods of future steps will always have access to the same
 * ID. In general, generating and storing IDs this way is a best practice in Stairway flights.
 */
public class GenerateResourceIdStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    workingMap.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, UUID.randomUUID());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
