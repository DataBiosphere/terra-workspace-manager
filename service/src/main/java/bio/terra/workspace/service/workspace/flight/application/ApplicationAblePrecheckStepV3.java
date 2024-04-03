package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.stairway.BaseStep;
import bio.terra.workspace.common.stairway.StepInput;
import bio.terra.workspace.common.stairway.StepOutput;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.exception.ApplicationNotFoundException;
import bio.terra.workspace.db.exception.InvalidApplicationStateException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.UUID;

// This step is shared by enable and disable to check the current enabled states of the
// application.
public class ApplicationAblePrecheckStepV3 extends BaseStep {
  private final ApplicationDao applicationDao;
  private final SamService samService;
  private final String applicationId;
  @StepInput private AuthenticatedUserRequest authUserInfo;
  @StepInput private UUID workspaceId;
  @StepInput private AbleEnum applicationAbleEnum;
  @StepOutput private WsmApplication wsmApplication;
  @StepOutput private boolean applicationAbleDao;
  @StepOutput private boolean applicationAbleSam;

  public ApplicationAblePrecheckStepV3(
      ApplicationDao applicationDao, SamService samService, String applicationId) {
    this.applicationDao = applicationDao;
    this.samService = samService;
    this.applicationId = applicationId;
  }

  @Override
  public StepResult perform() throws InterruptedException, RetryException {
    wsmApplication = applicationDao.getApplication(applicationId);

    // For enable, we require that the application be in the operating state
    if (applicationAbleEnum == AbleEnum.ENABLE) {
      if (wsmApplication.getState() != WsmApplicationState.OPERATING) {
        throw new InvalidApplicationStateException(
            "Applications is " + wsmApplication.getState().toApi() + " and cannot be enabled");
      }
    }

    // See if the application is enabled
    try {
      WsmWorkspaceApplication workspaceApp =
          applicationDao.getWorkspaceApplication(workspaceId, applicationId);
      applicationAbleDao = computeCorrectState(applicationAbleEnum, workspaceApp.isEnabled());
    } catch (ApplicationNotFoundException e) {
      applicationAbleDao = computeCorrectState(applicationAbleEnum, false);
    }

    // See if the application already has APPLICATION role for the workspace
    boolean enabledSam =
        samService.isApplicationEnabledInSam(
            workspaceId, wsmApplication.getServiceAccount(), authUserInfo);
    applicationAbleSam = computeCorrectState(applicationAbleEnum, enabledSam);

    return StepResult.getStepResultSuccess();
  }

  private boolean computeCorrectState(AbleEnum ableEnum, boolean isEnabled) {
    if (ableEnum == AbleEnum.ENABLE) {
      return isEnabled;
    }
    return !isEnabled;
  }
}
