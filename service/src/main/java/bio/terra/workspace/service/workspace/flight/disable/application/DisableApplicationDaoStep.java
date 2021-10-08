package bio.terra.workspace.service.workspace.flight.disable.application;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisableApplicationDaoStep implements Step {

  private final ApplicationDao applicationDao;
  private final UUID workspaceId;
  private final UUID applicationId;

  private final Logger logger = LoggerFactory.getLogger(DisableApplicationDaoStep.class);

  public DisableApplicationDaoStep(
      ApplicationDao applicationDao, UUID workspaceId, UUID applicationId) {
    this.applicationDao = applicationDao;
    this.workspaceId = workspaceId;
    this.applicationId = applicationId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws RetryException, InterruptedException {
    applicationDao.disableWorkspaceApplication(workspaceId, applicationId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We want to enable the application even if the application is deprecated, so
    // use the NoCheck variant of the DAO.
    applicationDao.enableWorkspaceApplicationNoCheck(workspaceId, applicationId);

    // Set up the flight result
    WsmWorkspaceApplication wsmApp =
        applicationDao.getWorkspaceApplication(workspaceId, applicationId);
    flightContext.getWorkingMap().put(JobMapKeys.RESPONSE.getKeyName(), wsmApp);

    return StepResult.getStepResultSuccess();
  }
}
