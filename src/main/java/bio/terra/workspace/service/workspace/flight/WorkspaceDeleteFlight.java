package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import org.jetbrains.annotations.NotNull;

public class WorkspaceDeleteFlight extends Flight {
  private static final int INITIAL_INTERVALS_SECONDS = 1;
  private static final int MAX_INTERVAL_SECONDS = 8;
  private static final int MAX_OPERATION_TIME_SECONDS = 5 * 60;

  public WorkspaceDeleteFlight(
      @NotNull FlightMap inputParameters, @NotNull Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    CrlService crl = appContext.getCrlService();

    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    // TODO: we still need the following steps once their features are supported:
    // 1. delete controlled resources using the Cloud Resource Manager library
    // 2. Notify all registered applications of deletion, once applications are supported
    // 3. Delete policy objects in Policy Manager, once it exists.

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            INITIAL_INTERVALS_SECONDS, MAX_INTERVAL_SECONDS, MAX_OPERATION_TIME_SECONDS);

    addStep(
        new DeleteProjectStep(crl.getCloudResourceManagerCow(), appContext.getWorkspaceDao()),
        retryRule);
    addStep(new DeleteWorkspaceAuthzStep(appContext.getSamService(), userReq));
    addStep(new DeleteWorkspaceStateStep(appContext.getWorkspaceDao()));
  }
}
