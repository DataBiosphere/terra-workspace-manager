package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting the metadata that WSM stores for a controlled resource. */
public class DeleteMetadataStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  private final WorkspaceActivityLogService workspaceActivityLogService;

  private final Logger logger = LoggerFactory.getLogger(DeleteMetadataStep.class);

  public DeleteMetadataStep(
      ResourceDao resourceDao,
      UUID workspaceUuid,
      UUID resourceId,
      WorkspaceActivityLogService workspaceActivityLogService) {
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
    this.resourceId = resourceId;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // retrieve the resource type of the resource to be deleted for logging.
    ActivityLogChangedTarget changedTarget =
        resourceDao
            .getResource(workspaceUuid, resourceId)
            .getResourceType()
            .getActivityLogChangedTarget();
    resourceDao.deleteResourceSuccess(workspaceUuid, resourceId, flightContext.getFlightId());

    AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            flightContext.getInputParameters(),
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    workspaceActivityLogService.writeActivity(
        userRequest, workspaceUuid, OperationType.DELETE, resourceId.toString(), changedTarget);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of WSM resource {} in workspace {}.", resourceId, workspaceUuid);
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
