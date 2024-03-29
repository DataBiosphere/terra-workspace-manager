package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.exception.ApplicationNotFoundException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ApplicationAbleSupportImpl implements ApplicationAbleSupport {
  private final ApplicationDao applicationDao;
  private final SamService samService;

  public ApplicationAbleSupportImpl(ApplicationDao applicationDao, SamService samService) {
    this.applicationDao = applicationDao;
    this.samService = samService;
  }

  @Override
  public ApplicationInfo getApplicationInfo(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      String applicationId,
      AbleEnum ableEnum) {

    WsmApplication application = applicationDao.getApplication(applicationId);

    boolean enabledInSam =
        samService.isApplicationEnabledInSam(
            workspaceUuid, application.getServiceAccount(), userRequest);

    return new ApplicationInfoImpl(
        application,
        computeCorrectState(ableEnum, isApplicationEnabled(workspaceUuid, applicationId)),
        computeCorrectState(ableEnum, enabledInSam));
  }

  @Override
  public void updateIamStep(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      AbleEnum ableEnum,
      ApplicationInfo applicationInfo)
      throws InterruptedException {
    if (applicationInfo.samAlreadyCorrect()) {
      return;
    }

    if (ableEnum == AbleEnum.ENABLE) {
      samService.grantWorkspaceRole(
          workspaceUuid,
          userRequest,
          WsmIamRole.APPLICATION,
          applicationInfo.application().getServiceAccount());
    } else {
      samService.removeWorkspaceRole(
          workspaceUuid,
          userRequest,
          WsmIamRole.APPLICATION,
          applicationInfo.application().getServiceAccount());
    }
  }

  @Override
  public void undoUpdateIamStep(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      AbleEnum ableEnum,
      ApplicationInfo applicationInfo)
      throws InterruptedException {
    updateIamStep(userRequest, workspaceUuid, ableEnum.toggle(), applicationInfo);
  }

  @Override
  public ApplicationAbleResult updateDatabaseStep(
      UUID workspaceUuid,
      String applicationId,
      AbleEnum ableEnum,
      ApplicationInfo applicationInfo) {
    if (applicationInfo.applicationAlreadyCorrect()) {
      return null;
    }
    WsmWorkspaceApplication wsmApp;
    if (ableEnum == AbleEnum.ENABLE) {
      wsmApp = applicationDao.enableWorkspaceApplication(workspaceUuid, applicationId);
    } else {
      wsmApp = applicationDao.disableWorkspaceApplication(workspaceUuid, applicationId);
    }
    return new ApplicationAbleResultImpl(wsmApp);
  }

  @Override
  public void undoUpdateDatabaseStep(
      UUID workspaceUuid,
      String applicationId,
      AbleEnum ableEnum,
      ApplicationInfo applicationInfo) {
    updateDatabaseStep(workspaceUuid, applicationId, ableEnum.toggle(), applicationInfo);
  }

  private boolean isApplicationEnabled(UUID workspaceUuid, String applicationId) {
    try {
      return applicationDao.getWorkspaceApplication(workspaceUuid, applicationId).isEnabled();
    } catch (ApplicationNotFoundException e) {
      return false;
    }
  }

  private boolean computeCorrectState(AbleEnum ableEnum, boolean isEnabled) {
    if (ableEnum == AbleEnum.ENABLE) {
      return isEnabled;
    }
    return !isEnabled;
  }
}
