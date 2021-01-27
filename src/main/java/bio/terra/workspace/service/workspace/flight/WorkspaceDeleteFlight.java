package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import org.springframework.context.ApplicationContext;

public class WorkspaceDeleteFlight extends Flight {

  public WorkspaceDeleteFlight(FlightMap inputParameters, Object applicationContext)
      throws Exception {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    WorkspaceDao workspaceDao = appContext.getBean(WorkspaceDao.class);
    SamService iamClient = appContext.getBean(SamService.class);
    CrlService crl = appContext.getBean(CrlService.class);

    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    // TODO: we still need the following steps once their features are supported:
    // 1. delete controlled resources using the Cloud Resource Manager library
    // 2. Notify all registered applications of deletion, once applications are supported
    // 3. Delete policy objects in Policy Manager, once it exists.

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            /* initialIntervalSeconds= */ 1,
            /* maxIntervalSeconds= */ 8,
            /* maxOperationTimeSeconds= */ 5 * 60);
    addStep(new DeleteProjectStep(crl.getCloudResourceManagerCow(), workspaceDao), retryRule);
    addStep(new DeleteWorkspaceAuthzStep(iamClient, userReq));
    addStep(new DeleteWorkspaceStateStep(workspaceDao));
  }
}
