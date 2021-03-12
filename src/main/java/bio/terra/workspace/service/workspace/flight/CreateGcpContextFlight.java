package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.crl.CrlService;

/**
 * A {@link Flight} for creating a Google cloud context for a workspace using Buffer Service to
 * create the project.
 */
public class CreateGcpContextFlight extends Flight {
  // Buffer Retry rule settings. For Buffer Service, allow for long wait times.
  // If the pool is empty, Buffer Service may need time to actually create a new project.
  private static final int BUFFER_INITIAL_INTERVAL_SECONDS = 1;
  private static final int BUFFER_MAX_INTERVAL_SECONDS = 5 * 60;
  private static final int BUFFER_MAX_OPERATION_TIME_SECONDS = 15 * 60;
  // Retry rule settings
  private static final int INITIAL_INTERVAL_SECONDS = 1;
  private static final int MAX_INTERVAL_SECONDS = 8;
  private static final int MAX_OPERATION_TIME_SECONDS = 16;

  public CreateGcpContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    CrlService crl = appContext.getCrlService();

    RetryRule bufferRetryRule =
        new RetryRuleExponentialBackoff(
            BUFFER_INITIAL_INTERVAL_SECONDS,
            BUFFER_MAX_INTERVAL_SECONDS,
            BUFFER_MAX_OPERATION_TIME_SECONDS);

    addStep(new GenerateProjectIdStep());
    addStep(
        new PullProjectFromPoolStep(
            appContext.getBufferService(), crl.getCloudResourceManagerCow()),
        bufferRetryRule);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            INITIAL_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS, MAX_OPERATION_TIME_SECONDS);
    addStep(new SetProjectBillingStep(crl.getCloudBillingClientCow()));
    addStep(new CreateCustomGcpRolesStep(crl.getIamCow()), retryRule);
    addStep(new StoreGcpContextStep(appContext.getWorkspaceDao()), retryRule);
    addStep(new SyncSamGroupsStep(appContext.getSamService()), retryRule);
    addStep(new GcpCloudSyncStep(crl.getCloudResourceManagerCow()), retryRule);
    addStep(new SetGcpContextOutputStep());
  }
}
