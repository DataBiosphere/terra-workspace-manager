package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.app.configuration.external.GoogleWorkspaceConfiguration;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
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
    CrlService crl = appContext.getBean(CrlService.class);
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
        new CreateProjectStep(
            crl.cloudResourceManagerCow(), crl.serviceUsageCow(), googleWorkspaceConfiguration),
        retryRule);
    addStep(new SetProjectBillingStep(crl.cloudBillingClientCow()));
    addStep(new StoreGoogleContextStep(workspaceDao, transactionTemplate), retryRule);
    addStep(new SyncSamGroupsStep(samService), retryRule);
    addStep(new GoogleCloudSyncStep(crl.cloudResourceManagerCow()), retryRule);
  }
}
