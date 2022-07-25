package bio.terra.workspace.common.logging;

import static com.google.common.base.Preconditions.checkNotNull;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import bio.terra.workspace.common.exception.UnhandledDeletionFlightException;
import bio.terra.workspace.common.logging.model.ActivityFlight;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceActivityLogHook implements StairwayHook {
  private static final Logger logger = LoggerFactory.getLogger(StairwayHook.class);

  private final WorkspaceActivityLogDao activityLogDao;
  private final WorkspaceDao workspaceDao;
  private final ResourceDao resourceDao;
  private final SamService samService;

  @Autowired
  public WorkspaceActivityLogHook(
      WorkspaceActivityLogDao activityLogDao,
      WorkspaceDao workspaceDao,
      ResourceDao resourceDao,
      SamService samService) {
    this.activityLogDao = activityLogDao;
    this.workspaceDao = workspaceDao;
    this.resourceDao = resourceDao;
    this.samService = samService;
  }

  @Override
  public HookAction endFlight(FlightContext context) throws InterruptedException {
    logger.info(
        String.format("endFlight %s: %s", context.getFlightClassName(), context.getFlightStatus()));
    var workspaceId =
        context.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class);
    var operationType =
        context
            .getInputParameters()
            .get(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.class);

    if (operationType == null) {
      // The operation type will only be null if the flight is launched directly through stairway
      // and skipped JobService. This should only happen to sub-flight and in test. We skip the
      // activity logging in these cases.
      logger.warn("Operation type is null, this is only OK if it's from a sub-flight");
      return HookAction.CONTINUE;
    }

    var userRequest =
        checkNotNull(
            context
                .getInputParameters()
                .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class));
    var userStatusInfo = samService.getUserStatusInfo(userRequest);
    var userEmail = userStatusInfo.getUserEmail();
    var subjectId = userStatusInfo.getUserSubjectId();

    ActivityFlight af = ActivityFlight.fromFlightClassName(context.getFlightClassName());
    UUID workspaceUuid = UUID.fromString(workspaceId);
    if (context.getFlightStatus() == FlightStatus.SUCCESS) {
      activityLogDao.writeActivity(
          workspaceUuid, getDbWorkspaceActivityLog(operationType, userEmail, subjectId));
      return HookAction.CONTINUE;
    }
    if (operationType != OperationType.DELETE) {
      return HookAction.CONTINUE;
    }
    // If DELETE flight failed, cloud resource may or may not have been deleted. Check if cloud
    // resource was deleted. If so, write to activity log.
    switch (af.getActivityLogChangedTarget()) {
      case WORKSPACE -> maybeLogWorkspaceDeletionFlight(workspaceUuid, userEmail, subjectId);
      case AZURE_CLOUD_CONTEXT -> maybeLogCloudContextDeletionFlight(
          CloudPlatform.AZURE, workspaceUuid, userEmail, subjectId);
      case GCP_CLOUD_CONTEXT -> maybeLogCloudContextDeletionFlight(
          CloudPlatform.GCP, workspaceUuid, userEmail, subjectId);
      case RESOURCE -> maybeLogControlledResourceDeletion(
          context, workspaceUuid, userEmail, subjectId);
      default -> throw new UnhandledDeletionFlightException(
          String.format(
              "Activity log should be updated for deletion flight %s failures",
              context.getFlightClassName()));
    }
    return HookAction.CONTINUE;
  }

  private void maybeLogWorkspaceDeletionFlight(
      UUID workspaceUuid, String userEmail, String subjectId) {
    try {
      workspaceDao.getWorkspace(workspaceUuid);
      logger.warn(
          String.format(
              "Workspace %s is failed to be deleted; "
                  + "not writing deletion to workspace activity log",
              workspaceUuid));
    } catch (WorkspaceNotFoundException e) {
      activityLogDao.writeActivity(
          workspaceUuid, getDbWorkspaceActivityLog(OperationType.DELETE, userEmail, subjectId));
    }
  }

  private void maybeLogCloudContextDeletionFlight(
      CloudPlatform cloudPlatform, UUID workspaceUuid, String userEmail, String subjectId) {
    Optional<String> cloudContext = workspaceDao.getCloudContext(workspaceUuid, cloudPlatform);
    if (cloudContext.isEmpty()) {
      activityLogDao.writeActivity(
          workspaceUuid, getDbWorkspaceActivityLog(OperationType.DELETE, userEmail, subjectId));
    } else {
      logger.warn(
          String.format(
              "CloudContext in workspace %s deletion fails; not writing deletion "
                  + "to workspace activity log",
              workspaceUuid));
    }
  }

  private void maybeLogControlledResourceDeletion(
      FlightContext context, UUID workspaceUuid, String userEmail, String subjectId) {
    var controlledResource =
        checkNotNull(
            context.getInputParameters().get(ResourceKeys.RESOURCE, ControlledResource.class));
    UUID resourceId = controlledResource.getResourceId();
    try {
      resourceDao.getResource(workspaceUuid, resourceId);
      logger.warn(
          String.format(
              "Controlled resource %s in workspace %s is failed to be deleted; "
                  + "not writing deletion to workspace activity log",
              resourceId, workspaceUuid));
    } catch (ResourceNotFoundException e) {
      activityLogDao.writeActivity(
          workspaceUuid, getDbWorkspaceActivityLog(OperationType.DELETE, userEmail, subjectId));
    }
  }

  private DbWorkspaceActivityLog getDbWorkspaceActivityLog(
      OperationType operationType, String email, String subjectId) {
    return new DbWorkspaceActivityLog()
        .operationType(operationType)
        .changeAgentEmail(email)
        .changeAgentSubjectId(subjectId);
  }
}
