package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.stairway.BaseStep;
import bio.terra.workspace.common.stairway.StepInput;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.UUID;

public class ApplicationAbleDaoStepV3 extends BaseStep {
  private final ApplicationDao applicationDao;
  private final String applicationId;
  @StepInput private UUID workspaceId;
  @StepInput private AbleEnum applicationAbleEnum;
  @StepInput private boolean applicationAbleDao;

  public ApplicationAbleDaoStepV3(ApplicationDao applicationDao, String applicationId) {
    this.applicationDao = applicationDao;
    this.applicationId = applicationId;
  }

  @Override
  public StepResult perform() throws InterruptedException, RetryException {

    // if the application was in the correct database state in precheck, we do nothing
    if (applicationAbleDao) {
      return StepResult.getStepResultSuccess();
    }

    WsmWorkspaceApplication wsmApp;
    if (applicationAbleEnum == AbleEnum.ENABLE) {
      wsmApp = applicationDao.enableWorkspaceApplication(workspaceId, applicationId);
    } else {
      wsmApp = applicationDao.disableWorkspaceApplication(workspaceId, applicationId);
    }
    setResponse(wsmApp);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undo() throws InterruptedException {
    // if the application was in the correct database state in precheck, we do nothing
    if (applicationAbleDao) {
      return StepResult.getStepResultSuccess();
    }

    if (applicationAbleEnum == AbleEnum.ENABLE) {
      applicationDao.disableWorkspaceApplication(workspaceId, applicationId);
    } else {
      applicationDao.enableWorkspaceApplication(workspaceId, applicationId);
    }
    return StepResult.getStepResultSuccess();
  }
}
