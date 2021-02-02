package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Generates Project Id and put it in working map.
 *
 * <p>This is important to do as a separate step from the project creation so that we always create
 * at most one project id, and always know which project id to try to delete.
 *
 * <p>TODO(PF-156): Use RBS for project creation instead.
 */
public class GenerateProjectIdStep implements Step {
  public GenerateProjectIdStep() {}

  @Override
  public StepResult doStep(@NotNull FlightContext flightContext) {
    flightContext.getWorkingMap().put(GOOGLE_PROJECT_ID, randomProjectId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  /** Generate a pseudo-random project id. */
  public static @NotNull String randomProjectId() {
    return "wm-" + Long.valueOf(UUID.randomUUID().getMostSignificantBits()).toString();
  }
}
