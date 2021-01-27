package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.crl.CrlService;

// NOTE: DO NOT USE. Currently just a shell class to exercise connection to Buffer Service.
public class CreateGoogleContextRBSFlight extends Flight {
  // Retry rule settings
  private static final int INITIAL_INTERVAL_SECONDS = 1;
  private static final int MAX_INTERVAL_SECONDS = 5 * 60;
  // TODO: This makes no sense to have a max operation time of sixteen seconds and an interval of
  // five minutes!
  private static final int MAX_OPERATION_TIME_SECONDS = 16;

  public CreateGoogleContextRBSFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = (FlightBeanBag) applicationContext;
    CrlService crl = appContext.getCrlService();

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            INITIAL_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS, MAX_OPERATION_TIME_SECONDS);

    addStep(new GenerateResourceIdStep());
    addStep(
        new PullProjectFromPoolStep(
            appContext.getBufferService(), crl.getCloudResourceManagerCow()),
        retryRule);
  }
}
