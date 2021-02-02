package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Stairway step to generate a unique id for a data reference.
 *
 * <p>Stairway's working map is only persisted at step boundaries. By generating an ID in its own
 * step, we ensure that both do and undo methods of future steps will always have access to the same
 * ID. In general, generating and storing IDs this way is a best practice in Stairway flights.
 */
public class GenerateReferenceIdStep implements Step {

  @Override
  public StepResult doStep(@NotNull FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    UUID referenceId = UUID.randomUUID();
    workingMap.put(DataReferenceFlightMapKeys.REFERENCE_ID, referenceId);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
