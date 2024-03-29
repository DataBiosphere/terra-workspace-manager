package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.exception.ApplicationNotFoundException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.model.WsmApplication;
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
        isStateCorrect(ableEnum, isApplicationEnabled(workspaceUuid, applicationId)),
        isStateCorrect(ableEnum, enabledInSam));
  }

  @Override
  public void grantApplicationIam(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, ApplicationInfo applicationInfo)
      throws InterruptedException {
    if (!applicationInfo.samAlreadyCorrect()) {
      samService.grantWorkspaceRole(
          workspaceUuid,
          userRequest,
          WsmIamRole.APPLICATION,
          applicationInfo.application().getServiceAccount());
    }
  }

  @Override
  public void revokeApplicationIam(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, ApplicationInfo applicationInfo)
      throws InterruptedException {
    if (!applicationInfo.samAlreadyCorrect()) {
      samService.removeWorkspaceRole(
          workspaceUuid,
          userRequest,
          WsmIamRole.APPLICATION,
          applicationInfo.application().getServiceAccount());
    }
  }

  @Override
  public ApplicationAbleResult enableWorkspaceApplication(
      UUID workspaceUuid, ApplicationInfo applicationInfo) {
    if (applicationInfo.applicationAlreadyCorrect()) {
      return null;
    } else {
      return new ApplicationAbleResultImpl(
          applicationDao.enableWorkspaceApplication(
              workspaceUuid, applicationInfo.application().getApplicationId()));
    }
  }

  @Override
  public ApplicationAbleResult disableWorkspaceApplication(
      UUID workspaceUuid, ApplicationInfo applicationInfo) {
    if (applicationInfo.applicationAlreadyCorrect()) {
      return null;
    } else {
      return new ApplicationAbleResultImpl(
          applicationDao.enableWorkspaceApplication(
              workspaceUuid, applicationInfo.application().getApplicationId()));
    }
  }

  private boolean isApplicationEnabled(UUID workspaceUuid, String applicationId) {
    try {
      return applicationDao.getWorkspaceApplication(workspaceUuid, applicationId).isEnabled();
    } catch (ApplicationNotFoundException e) {
      return false;
    }
  }

  private boolean isStateCorrect(AbleEnum ableEnum, boolean isEnabled) {
    return (ableEnum == AbleEnum.ENABLE) == isEnabled;
  }
}
