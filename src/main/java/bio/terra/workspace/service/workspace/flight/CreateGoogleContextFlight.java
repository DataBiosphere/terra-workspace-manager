package bio.terra.workspace.service.workspace.flight;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.cloudres.google.serviceusage.ServiceUsageCow;
import bio.terra.stairway.*;
import bio.terra.workspace.app.configuration.external.GoogleWorkspaceConfiguration;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.SamService;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/** A {@link Flight} for creating a Google cloud context for a workspace. */
public class CreateGoogleContextFlight extends Flight {

  public CreateGoogleContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    GoogleWorkspaceConfiguration googleWorkspaceConfiguration =
        appContext.getBean(GoogleWorkspaceConfiguration.class);
    CloudResourceManagerCow resourceManager = appContext.getBean(CloudResourceManagerCow.class);
    ServiceUsageCow serviceUsage = appContext.getBean(ServiceUsageCow.class);
    CloudBillingClientCow billingClient = appContext.getBean(CloudBillingClientCow.class);
    WorkspaceDao workspaceDao = appContext.getBean(WorkspaceDao.class);
    TransactionTemplate transactionTemplate = appContext.getBean(TransactionTemplate.class);
    SamService samService = appContext.getBean(SamService.class);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            /* initialIntervalSeconds= */ 1,
            /* maxIntervalSeconds= */ 8,
            /* maxOperationTimeSeconds= */ 16);
    addStep(new GenerateProjectIdStep());
    addStep(
        new CreateProjectStep(resourceManager, serviceUsage, googleWorkspaceConfiguration),
        retryRule);
    addStep(new SetProjectBillingStep(billingClient));
    addStep(new StoreGoogleContextStep(workspaceDao, transactionTemplate), retryRule);
    addStep(new SyncSamGroupsStep(samService), retryRule);
    addStep(new GoogleCloudSyncStep(resourceManager), retryRule);
  }
}
