package bio.terra.workspace.common.utils;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate flight ids and store them in the working map. The ids need to be stable across runs of
 * the RunDeleteCloudContextFlightStep.
 */
public class MakeFlightIdsStep implements Step {
  private final List<String> keys;
  private final String flightMapKey;

  public MakeFlightIdsStep(List<String> keys, String flightMapKey) {
    this.keys = keys;
    this.flightMapKey = flightMapKey;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Map<String, String> flightIds = new HashMap<>();
    keys.forEach(
        key -> {
          String flightId = context.getStairway().createFlightId();
          flightIds.put(key, flightId);
        });

    context.getWorkingMap().put(flightMapKey, flightIds);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
