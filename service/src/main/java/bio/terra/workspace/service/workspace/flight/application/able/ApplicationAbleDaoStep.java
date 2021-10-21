package bio.terra.workspace.service.workspace.flight.application.able;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WsmApplicationKeys;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationAbleDaoStep implements Step {
  private final static Logger logger = LoggerFactory.getLogger(ApplicationAbleDaoStep.class);

  private final ApplicationDao applicationDao;
  private final UUID workspaceId;
  private final UUID applicationId;
  private final AbleEnum ableEnum;

  public ApplicationAbleDaoStep(
      ApplicationDao applicationDao,
      UUID workspaceId,
      UUID applicationId,
      AbleEnum ableEnum) {
    this.applicationDao = applicationDao;
    this.workspaceId = workspaceId;
    this.applicationId = applicationId;
    this.ableEnum = ableEnum;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();

    FlightUtils.validateRequiredEntries(
        workingMap,
        WsmApplicationKeys.APPLICATION_ABLE_DAO,
        WsmApplicationKeys.WSM_APPLICATION);

    // if the application was in the correct database state in precheck, we do nothing
    if (workingMap.get(WsmApplicationKeys.APPLICATION_ABLE_DAO, Boolean.class)) {
      return StepResult.getStepResultSuccess();
    }

    WsmWorkspaceApplication wsmApp;
    if (ableEnum == AbleEnum.ENABLE) {
      wsmApp = applicationDao.enableWorkspaceApplication(workspaceId, applicationId);
    } else {
      wsmApp = applicationDao.disableWorkspaceApplication(workspaceId, applicationId);
    }
    workingMap.put(JobMapKeys.RESPONSE.getKeyName(), wsmApp);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();

    // if the application was in the correct database state in precheck, we do nothing
    if (workingMap.get(WsmApplicationKeys.APPLICATION_ABLE_DAO, Boolean.class)) {
      return StepResult.getStepResultSuccess();
    }

    if (ableEnum == AbleEnum.ENABLE) {
      applicationDao.disableWorkspaceApplication(workspaceId, applicationId);
    } else {
      applicationDao.enableWorkspaceApplication(workspaceId, applicationId);
    }
    return StepResult.getStepResultSuccess();
  }

}
