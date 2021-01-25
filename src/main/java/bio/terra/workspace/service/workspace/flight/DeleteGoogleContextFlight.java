package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.crl.CrlService;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/** A {@link Flight} for deleting a Google cloud context for a workspace. */
public class DeleteGoogleContextFlight extends Flight {
  public DeleteGoogleContextFlight(FlightMap inputParameters, Object applicationContext)
      throws Exception {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    CrlService crl = appContext.getBean(CrlService.class);
    WorkspaceDao workspaceDao = appContext.getBean(WorkspaceDao.class);
    TransactionTemplate transactionTemplate = appContext.getBean(TransactionTemplate.class);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            /* initialIntervalSeconds= */ 1,
            /* maxIntervalSeconds= */ 8,
            /* maxOperationTimeSeconds= */ 5 * 60);
    addStep(new DeleteProjectStep(crl.cloudResourceManagerCow(), workspaceDao), retryRule);
    addStep(new DeleteGoogleContextStep(workspaceDao, transactionTemplate), retryRule);
  }
}
