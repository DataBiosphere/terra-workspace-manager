package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;

// NOTE: DO NOT USE. Currently just a shell class to exercise connection to Buffer Service.
public class CreateGoogleContextRBSFlight extends Flight {
  public CreateGoogleContextRBSFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            /* initialIntervalSeconds= */ 1,
            /* maxIntervalSeconds= */ 5 * 60,
            /* maxOperationTimeSeconds= */ 16);
    addStep(new GenerateResourceIdStep());
    addStep(
        new PullProjectFromPoolStep(appContext.getBufferService(), appContext.getResourceManager()),
        retryRule);
  }
}
