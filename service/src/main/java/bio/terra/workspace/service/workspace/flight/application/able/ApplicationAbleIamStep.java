package bio.terra.workspace.service.workspace.flight.application.able;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WsmApplicationKeys;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationAbleIamStep implements Step {
  private final static Logger logger = LoggerFactory.getLogger(ApplicationAbleIamStep.class);

  private final SamService samService;
  private final UUID workspaceId;
  private final AbleEnum ableEnum;

  public ApplicationAbleIamStep(
      SamService samService,
      UUID workspaceId,
      AbleEnum ableEnum) {
    this.samService = samService;
    this.workspaceId = workspaceId;
    this.ableEnum = ableEnum;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();

    FlightUtils.validateRequiredEntries(
        workingMap,
        WsmApplicationKeys.WSM_APPLICATION,
        WsmApplicationKeys.APPLICATION_ABLE_SAM);

    // if the application was in the correct Sam state in precheck, then we do nothing
    if (workingMap.get(WsmApplicationKeys.APPLICATION_ABLE_SAM, Boolean.class)) {
      return StepResult.getStepResultSuccess();
    }

    WsmApplication application =
        workingMap.get(WsmApplicationKeys.WSM_APPLICATION, WsmApplication.class);

    if (ableEnum == AbleEnum.ENABLE) {
      samService.grantWorkspaceRoleAsWsm(
          workspaceId, WsmIamRole.APPLICATION, application.getServiceAccount());
    } else {
      samService.removeWorkspaceRoleAsWsm(
          workspaceId, WsmIamRole.APPLICATION, application.getServiceAccount());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();

    // if the application was not already enabled in Sam when we started, we do not undo it
    if (workingMap.get(WsmApplicationKeys.APPLICATION_ABLE_SAM, Boolean.class)) {
      return StepResult.getStepResultSuccess();
    }

    WsmApplication application =
        workingMap.get(WsmApplicationKeys.WSM_APPLICATION, WsmApplication.class);
    if (ableEnum == AbleEnum.ENABLE) {
      samService.removeWorkspaceRoleAsWsm(
          workspaceId, WsmIamRole.APPLICATION, application.getServiceAccount());
    } else {
      samService.grantWorkspaceRoleAsWsm(
          workspaceId, WsmIamRole.APPLICATION, application.getServiceAccount());
    }

    return StepResult.getStepResultSuccess();
  }
}
