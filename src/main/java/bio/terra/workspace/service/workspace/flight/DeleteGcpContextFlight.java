package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;

/** A {@link Flight} for deleting a Google cloud context for a workspace. */
public class DeleteGcpContextFlight extends Flight {
  private static final int INITIAL_INTERVALS_SECONDS = 1;
  private static final int MAX_INTERVAL_SECONDS = 8;
  private static final int MAX_OPERATION_TIME_SECONDS = 5 * 60;

  public DeleteGcpContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            INITIAL_INTERVALS_SECONDS, MAX_INTERVAL_SECONDS, MAX_OPERATION_TIME_SECONDS);

    addStep(
        new DeleteProjectStep(appContext.getCrlService(), appContext.getWorkspaceDao()), retryRule);
    addStep(new DeleteGcpContextStep(appContext.getWorkspaceDao()), retryRule);
  }
}
