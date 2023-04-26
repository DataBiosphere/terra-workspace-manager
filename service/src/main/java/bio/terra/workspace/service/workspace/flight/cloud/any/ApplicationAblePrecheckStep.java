package bio.terra.workspace.service.workspace.flight.application.able;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.exception.ApplicationNotFoundException;
import bio.terra.workspace.db.exception.InvalidApplicationStateException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WsmApplicationKeys;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This step is shared by enable and disable to check the current enabled states of the
// application.
public class ApplicationAblePrecheckStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationAblePrecheckStep.class);

  private final ApplicationDao applicationDao;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;
  private final UUID workspaceUuid;
  private final String applicationId;
  private final AbleEnum ableEnum;

  public ApplicationAblePrecheckStep(
      ApplicationDao applicationDao,
      SamService samService,
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      String applicationId,
      AbleEnum ableEnum) {
    this.applicationDao = applicationDao;
    this.samService = samService;
    this.userRequest = userRequest;
    this.workspaceUuid = workspaceUuid;
    this.applicationId = applicationId;
    this.ableEnum = ableEnum;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    WsmApplication application = applicationDao.getApplication(applicationId);

    // For enable, we require that the application be in the operating state
    if (ableEnum == AbleEnum.ENABLE) {
      if (application.getState() != WsmApplicationState.OPERATING) {
        throw new InvalidApplicationStateException(
            "Applications is " + application.getState().toApi() + " and cannot be enabled");
      }
    }

    FlightMap workingMap = context.getWorkingMap();
    workingMap.put(WsmApplicationKeys.WSM_APPLICATION, application);

    // See if the application is enabled
    WsmWorkspaceApplication workspaceApp;
    try {
      workspaceApp = applicationDao.getWorkspaceApplication(workspaceUuid, applicationId);
      workingMap.put(
          WsmApplicationKeys.APPLICATION_ABLE_DAO,
          computeCorrectState(ableEnum, workspaceApp.isEnabled()));
    } catch (ApplicationNotFoundException e) {
      workingMap.put(WsmApplicationKeys.APPLICATION_ABLE_DAO, computeCorrectState(ableEnum, false));
    }

    // See if the application already has APPLICATION role for the workspace
    boolean enabledSam =
        samService.isApplicationEnabledInSam(
            workspaceUuid, application.getServiceAccount(), userRequest);
    workingMap.put(
        WsmApplicationKeys.APPLICATION_ABLE_SAM, computeCorrectState(ableEnum, enabledSam));

    return StepResult.getStepResultSuccess();
  }

  private boolean computeCorrectState(AbleEnum ableEnum, boolean isEnabled) {
    if (ableEnum == AbleEnum.ENABLE) {
      return isEnabled;
    }
    return !isEnabled;
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // This is a query-only step, so nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
