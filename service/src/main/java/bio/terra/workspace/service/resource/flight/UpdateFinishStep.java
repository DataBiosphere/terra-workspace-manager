package bio.terra.workspace.service.resource.flight;

import static java.lang.Boolean.TRUE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.UUID;

public class UpdateFinishStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  private final ActivityLogChangedTarget activityLogChangedTarget;

  private final WorkspaceActivityLogService workspaceActivityLogService;

  public UpdateFinishStep(
      ResourceDao resourceDao,
      WsmResource resource,
      WorkspaceActivityLogService workspaceActivityLogService) {
    this.resourceDao = resourceDao;
    this.resourceId = resource.getResourceId();
    this.workspaceUuid = resource.getWorkspaceId();
    this.activityLogChangedTarget = resource.getResourceType().getActivityLogChangedTarget();
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    flightContext.getWorkingMap().put(ResourceKeys.UPDATE_COMPLETE, Boolean.FALSE);
    DbUpdater dbUpdater =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), ResourceKeys.DB_UPDATER, DbUpdater.class);
    resourceDao.updateResourceSuccess(
        workspaceUuid, resourceId, dbUpdater, flightContext.getFlightId());
    flightContext.getWorkingMap().put(ResourceKeys.UPDATE_COMPLETE, TRUE);
    AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            flightContext.getInputParameters(),
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    workspaceActivityLogService.writeActivity(
        userRequest,
        workspaceUuid,
        OperationType.UPDATE,
        resourceId.toString(),
        activityLogChangedTarget);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    Boolean didUpdate =
        flightContext.getWorkingMap().get(ResourceKeys.UPDATE_COMPLETE, Boolean.class);
    if (TRUE.equals(didUpdate)) {
      // If the update is complete, then we cannot undo it. This is a teeny tiny window
      // where the error occurs after the update, but before the success return. However,
      // the DebugInfo.lastStepFailure will alwyas hit it.
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL, new RuntimeException("dismal failure"));
    }
    // Failed before update - perform undo
    return StepResult.getStepResultSuccess();
  }
}
