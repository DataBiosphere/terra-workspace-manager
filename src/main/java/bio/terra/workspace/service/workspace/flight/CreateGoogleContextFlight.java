package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.crl.CrlService;

/** A {@link Flight} for creating a Google cloud context for a workspace. */
public class CreateGoogleContextFlight extends Flight {
  // retry rule settings
  private static final int INITIAL_INTERVAL_SECONDS = 1;
  private static final int MAX_INTERVAL_SECONDS = 8;
  private static final int MAX_OPERATION_TIME_SECONDS = 16;

  public CreateGoogleContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = (FlightBeanBag) applicationContext;
    CrlService crl = appContext.getCrlService();

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            INITIAL_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS, MAX_OPERATION_TIME_SECONDS);

    addStep(new GenerateProjectIdStep());
    addStep(
        new CreateProjectStep(
            crl.getCloudResourceManagerCow(),
            crl.getServiceUsageCow(),
            appContext.getGoogleWorkspaceConfiguration()),
        retryRule);
    addStep(new SetProjectBillingStep(crl.getCloudBillingClientCow()));
    addStep(
        new StoreGoogleContextStep(
            appContext.getWorkspaceDao(), appContext.getTransactionTemplate()),
        retryRule);
    addStep(new SyncSamGroupsStep(appContext.getSamService()), retryRule);
    addStep(new GoogleCloudSyncStep(crl.getCloudResourceManagerCow()), retryRule);
  }
}
