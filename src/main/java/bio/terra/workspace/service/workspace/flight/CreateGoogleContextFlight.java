package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightApplicationContext;

/** A {@link Flight} for creating a Google cloud context for a workspace. */
public class CreateGoogleContextFlight extends Flight {

  public CreateGoogleContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightApplicationContext appContext =
        FlightApplicationContext.getFromObject(applicationContext);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            /* initialIntervalSeconds= */ 1,
            /* maxIntervalSeconds= */ 8,
            /* maxOperationTimeSeconds= */ 16);
    addStep(new GenerateProjectIdStep());
    addStep(
        new CreateProjectStep(
            appContext.getResourceManager(),
            appContext.getServiceUsage(),
            appContext.getGoogleWorkspaceConfiguration()),
        retryRule);
    addStep(new SetProjectBillingStep(appContext.getBillingClient()));
    addStep(
        new StoreGoogleContextStep(
            appContext.getWorkspaceDao(), appContext.getTransactionTemplate()),
        retryRule);
    addStep(new SyncSamGroupsStep(appContext.getSamService()), retryRule);
    addStep(new GoogleCloudSyncStep(appContext.getResourceManager()), retryRule);
  }
}
