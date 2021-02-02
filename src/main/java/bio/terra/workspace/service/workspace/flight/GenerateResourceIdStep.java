package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.RBS_RESOURCE_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Generates Resource Id and put it in working map.
 *
 * <p>This is important to do as a separate step from the request to RBS so that we can retry using
 * the same ID.
 */
public class GenerateResourceIdStep implements Step {
  public GenerateResourceIdStep() {}

  @Override
  public StepResult doStep(@NotNull FlightContext flightContext) {
    flightContext.getWorkingMap().put(RBS_RESOURCE_ID, randomResourceId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  /** Generate a pseudo-random resource id. */
  public static @NotNull String randomResourceId() {
    return "wm-" + Long.valueOf(UUID.randomUUID().getMostSignificantBits()).toString();
  }
}
