package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.stairway.BaseStep;
import bio.terra.workspace.common.stairway.StepInput;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import java.util.UUID;

public class ApplicationAbleIamStepV3 extends BaseStep {
  private final SamService samService;
  @StepInput private AuthenticatedUserRequest authUserInfo;
  @StepInput private UUID workspaceId;
  @StepInput private AbleEnum applicationAbleEnum;
  @StepInput private WsmApplication wsmApplication;
  @StepInput private boolean applicationAbleSam;

  public ApplicationAbleIamStepV3(SamService samService) {
    this.samService = samService;
  }

  @Override
  public StepResult perform() throws InterruptedException, RetryException {
    // if the application was in the correct Sam state in precheck, then we do nothing
    if (applicationAbleSam) {
      return StepResult.getStepResultSuccess();
    }

    if (applicationAbleEnum == AbleEnum.ENABLE) {
      samService.grantWorkspaceRole(
          workspaceId, authUserInfo, WsmIamRole.APPLICATION, wsmApplication.getServiceAccount());
    } else {
      samService.removeWorkspaceRole(
          workspaceId, authUserInfo, WsmIamRole.APPLICATION, wsmApplication.getServiceAccount());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undo() throws InterruptedException {

    // if the application was not already enabled in Sam when we started, we do not undo it
    if (applicationAbleSam) {
      return StepResult.getStepResultSuccess();
    }

    if (applicationAbleEnum == AbleEnum.ENABLE) {
      samService.removeWorkspaceRole(
          workspaceId, authUserInfo, WsmIamRole.APPLICATION, wsmApplication.getServiceAccount());
    } else {
      samService.grantWorkspaceRole(
          workspaceId, authUserInfo, WsmIamRole.APPLICATION, wsmApplication.getServiceAccount());
    }

    return StepResult.getStepResultSuccess();
  }
}
