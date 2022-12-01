package bio.terra.workspace.common.logging;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FOLDER_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.UPDATED_WORKSPACES;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import bio.terra.workspace.common.exception.UnhandledDeletionFlightException;
import bio.terra.workspace.common.logging.model.ActivityFlight;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.SyncGcpIamRolesFlight;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WsmObjectType;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceActivityLogHook implements StairwayHook {
  private static final Logger logger = LoggerFactory.getLogger(StairwayHook.class);

  private final FolderDao folderDao;
  private final WorkspaceActivityLogDao activityLogDao;
  private final WorkspaceDao workspaceDao;
  private final ResourceDao resourceDao;
  private final SamService samService;

  @Autowired
  public WorkspaceActivityLogHook(
      WorkspaceActivityLogDao activityLogDao,
      FolderDao folderDao,
      WorkspaceDao workspaceDao,
      ResourceDao resourceDao,
      SamService samService) {
    this.activityLogDao = activityLogDao;
    this.folderDao = folderDao;
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
    // Use email from userStatusInfo instead of AuthenticatedUserRequest, because
    // AuthenticatedUserRequest might have pet SA email
    var userEmail = userStatusInfo.getUserEmail();
    var subjectId = userStatusInfo.getUserSubjectId();

    ActivityFlight af = ActivityFlight.fromFlightClassName(context.getFlightClassName());
    if (workspaceId == null) {
      if (!SyncGcpIamRolesFlight.class.getName().equals(context.getFlightClassName())) {
        logger.warn(
            "workspace id is missing from the flight, this should not happen for {}",
            context.getFlightClassName());
        return HookAction.CONTINUE;
      }
      maybeLogForSyncGcpIamRolesFlight(context, operationType, userEmail, subjectId);
      return HookAction.CONTINUE;
    }
    UUID workspaceUuid = UUID.fromString(workspaceId);
    if (context.getFlightStatus() == FlightStatus.SUCCESS) {
      switch (af.getActivityLogChangedTarget()) {
        case WORKSPACE, AZURE_CLOUD_CONTEXT, GCP_CLOUD_CONTEXT -> activityLogDao.writeActivity(
            workspaceUuid,
            new DbWorkspaceActivityLog(
                userEmail,
                subjectId,
                operationType,
                workspaceUuid.toString(),
                WsmObjectType.WORKSPACE));
        case RESOURCE -> activityLogDao.writeActivity(
            workspaceUuid,
            new DbWorkspaceActivityLog(
                userEmail,
                subjectId,
                operationType,
                getSingleResourceId(context).toString(),
                WsmObjectType.RESOURCE));
        case FOLDER -> activityLogDao.writeActivity(
            workspaceUuid,
            new DbWorkspaceActivityLog(
                userEmail,
                subjectId,
                operationType,
                getRequired(context.getInputParameters(), FOLDER_ID, UUID.class).toString(),
                WsmObjectType.FOLDER));
        default -> throw new UnhandledDeletionFlightException(
            String.format(
                "Activity log should be updated for deletion flight %s failures",
                context.getFlightClassName()));
      }
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
      case FOLDER -> maybeLogFolderDeletion(context, workspaceUuid, userEmail, subjectId);
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
          workspaceUuid,
          new DbWorkspaceActivityLog(
              userEmail,
              subjectId,
              OperationType.DELETE,
              workspaceUuid.toString(),
              WsmObjectType.WORKSPACE));
    }
  }

  private void maybeLogCloudContextDeletionFlight(
      CloudPlatform cloudPlatform, UUID workspaceUuid, String userEmail, String subjectId) {
    Optional<String> cloudContext = workspaceDao.getCloudContext(workspaceUuid, cloudPlatform);
    if (cloudContext.isEmpty()) {
      activityLogDao.writeActivity(
          workspaceUuid,
          new DbWorkspaceActivityLog(
              userEmail,
              subjectId,
              OperationType.DELETE,
              workspaceUuid.toString(),
              WsmObjectType.WORKSPACE));
    } else {
      logger.warn(
          String.format(
              "CloudContext in workspace %s deletion fails; not writing deletion "
                  + "to workspace activity log",
              workspaceUuid));
    }
  }

  private void maybeLogFolderDeletion(
      FlightContext context, UUID workspaceUuid, String userEmail, String subjectId) {
    var folderId = getRequired(context.getInputParameters(), FOLDER_ID, UUID.class);
    if (folderDao.getFolderIfExists(workspaceUuid, folderId).isEmpty()) {
      activityLogDao.writeActivity(
          workspaceUuid,
          new DbWorkspaceActivityLog(
              userEmail,
              subjectId,
              OperationType.DELETE,
              folderId.toString(),
              WsmObjectType.FOLDER));
    }
  }

  private void maybeLogControlledResourceDeletion(
      FlightContext context, UUID workspaceUuid, String userEmail, String subjectId) {
    UUID resourceId = getSingleResourceId(context);
    try {
      resourceDao.getResource(workspaceUuid, resourceId);
      logger.warn(
          String.format(
              "Controlled resource %s in workspace %s is failed to be deleted; "
                  + "not writing deletion to workspace activity log",
              resourceId, workspaceUuid));
    } catch (ResourceNotFoundException e) {
      activityLogDao.writeActivity(
          workspaceUuid,
          new DbWorkspaceActivityLog(
              userEmail,
              subjectId,
              OperationType.DELETE,
              resourceId.toString(),
              WsmObjectType.RESOURCE));
    }
  }

  private UUID getSingleResourceId(FlightContext context) {
    List<ControlledResource> controlledResource =
        checkNotNull(
            context
                .getInputParameters()
                .get(CONTROLLED_RESOURCES_TO_DELETE, new TypeReference<>() {}));
    checkState(controlledResource.size() == 1);
    UUID resourceId = controlledResource.get(0).getResourceId();
    return resourceId;
  }

  private void maybeLogForSyncGcpIamRolesFlight(
      FlightContext context, OperationType operationType, String userEmail, String subjectId) {
    HashSet<String> updatedWorkspaces =
        Objects.requireNonNull(
            context.getWorkingMap().get(UPDATED_WORKSPACES, new TypeReference<>() {}));
    for (var id : updatedWorkspaces) {
      activityLogDao.writeActivity(
          UUID.fromString(id),
          new DbWorkspaceActivityLog(
              userEmail, subjectId, operationType, id, WsmObjectType.WORKSPACE));
    }
  }
}
