package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.RBS_RESOURCE_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import java.util.UUID;

/**
 * Generates Resource Id and put it in working map.
 *
 * <p>This is important to do as a separate step from the request to RBS so that we can retry using
 * the same ID.
 */
public class GenerateRbsRequestIdStep implements Step {
  public GenerateRbsRequestIdStep() {}

  @Override
  public StepResult doStep(FlightContext flightContext) {
    flightContext.getWorkingMap().put(RBS_RESOURCE_ID, randomResourceId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }

  /** Generate a pseudo-random resource id. */
  // TODO: Why not just use the UUID? Or use a short UUID (once I put that into common-lib)
  public static String randomResourceId() {
    return "wm-" + Long.valueOf(UUID.randomUUID().getMostSignificantBits()).toString();
  }
}
