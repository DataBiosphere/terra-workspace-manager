package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;

/** A {@link Flight} for deleting a Google cloud context for a workspace. */
public class DeleteGoogleContextFlight extends Flight {
  public DeleteGoogleContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            /* initialIntervalSeconds= */ 1,
            /* maxIntervalSeconds= */ 8,
            /* maxOperationTimeSeconds= */ 5 * 60);
    addStep(
        new DeleteProjectStep(appContext.getResourceManager(), appContext.getWorkspaceDao()),
        retryRule);
    addStep(
        new DeleteGoogleContextStep(
            appContext.getWorkspaceDao(), appContext.getTransactionTemplate()),
        retryRule);
  }
}
