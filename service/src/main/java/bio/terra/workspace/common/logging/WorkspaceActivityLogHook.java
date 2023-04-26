package bio.terra.workspace.common.logging;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.APPLICATION_IDS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.FOLDER_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.UPDATED_WORKSPACES;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.USER_TO_REMOVE;
import static com.google.common.base.Preconditions.checkNotNull;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import bio.terra.workspace.common.exception.UnhandledActivityLogException;
import bio.terra.workspace.common.exception.UnhandledDeletionFlightException;
import bio.terra.workspace.common.logging.model.ActivityFlight;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.FlightUtils;
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
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    String flightClassName = context.getFlightClassName();
    logger.info("endFlight {}: {}", flightClassName, context.getFlightStatus());
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

    ActivityFlight af = ActivityFlight.fromFlightClassName(flightClassName);
    if (workspaceId == null) {
      return maybeLogFlightWithoutWorkspaceId(
          context, flightClassName, operationType, userEmail, subjectId);
    }
    UUID workspaceUuid = UUID.fromString(workspaceId);
    // If DELETE flight failed, cloud resource may or may not have been deleted. Check if cloud
    // resource was deleted. If so, write to activity log.
    if (operationType == OperationType.DELETE) {
      switch (af.getActivityLogChangedTarget()) {
        case WORKSPACE -> maybeLogWorkspaceDeletionFlight(workspaceUuid, userEmail, subjectId);
        case GCP_CLOUD_CONTEXT -> maybeLogCloudContextDeletionFlight(
            CloudPlatform.GCP, workspaceUuid, userEmail, subjectId);
        case AZURE_CLOUD_CONTEXT -> maybeLogCloudContextDeletionFlight(
            CloudPlatform.AZURE, workspaceUuid, userEmail, subjectId);
        case AWS_CLOUD_CONTEXT -> maybeLogCloudContextDeletionFlight(
            CloudPlatform.AWS, workspaceUuid, userEmail, subjectId);
        case RESOURCE -> maybeLogControlledResourcesDeletionFlight(
            context, workspaceUuid, userEmail, subjectId);
        case FOLDER -> maybeLogFolderDeletionFlight(context, workspaceUuid, userEmail, subjectId);
        default -> throw new UnhandledDeletionFlightException(
            String.format(
                "Activity log should be updated for deletion flight %s failures", flightClassName));
      }
      return HookAction.CONTINUE;
    }
    // Always log when the flight succeeded.
    if (context.getFlightStatus() == FlightStatus.SUCCESS) {
      switch (af.getActivityLogChangedTarget()) {
        case WORKSPACE, AZURE_CLOUD_CONTEXT, GCP_CLOUD_CONTEXT -> activityLogDao.writeActivity(
            workspaceUuid,
            new DbWorkspaceActivityLog(
                userEmail,
                subjectId,
                operationType,
                getClonedWorkspaceId(context, operationType, workspaceUuid).toString(),
                af.getActivityLogChangedTarget()));
        case RESOURCE -> activityLogDao.writeActivity(
            getAffectedWorkspaceId(context, operationType, workspaceUuid),
            new DbWorkspaceActivityLog(
                userEmail,
                subjectId,
                operationType,
                getRequired(context.getInputParameters(), ResourceKeys.RESOURCE, WsmResource.class)
                    .getResourceId()
                    .toString(),
                af.getActivityLogChangedTarget()));
        case FOLDER -> activityLogDao.writeActivity(
            workspaceUuid,
            new DbWorkspaceActivityLog(
                userEmail,
                subjectId,
                operationType,
                getRequired(context.getInputParameters(), FOLDER_ID, UUID.class).toString(),
                af.getActivityLogChangedTarget()));
        case USER -> activityLogDao.writeActivity(
            workspaceUuid,
            new DbWorkspaceActivityLog(
                userEmail,
                subjectId,
                operationType,
                getRequired(context.getInputParameters(), USER_TO_REMOVE, String.class),
                af.getActivityLogChangedTarget()));
        case APPLICATION -> logApplicationAbleFlight(
            workspaceUuid, context, userEmail, subjectId, operationType);
      }
    }
    return HookAction.CONTINUE;
  }

  /**
   * For rare cases when a flight is missing workspace id, we should handle the logging on a
   * case-by-case basis.
   */
  private HookAction maybeLogFlightWithoutWorkspaceId(
      FlightContext context,
      String flightClassName,
      OperationType operationType,
      String userEmail,
      String subjectId) {
    if (SyncGcpIamRolesFlight.class.getName().equals(flightClassName)) {
      maybeLogForSyncGcpIamRolesFlight(context, operationType, userEmail, subjectId);
    } else {
      throw new UnhandledActivityLogException(
          String.format(
              "workspace id is missing from the flight %s, add special log handling",
              flightClassName));
    }
    return HookAction.CONTINUE;
  }

  /**
   * Get the workspace where the activity operation is acted upon. For cloning a workspace, it
   * should be the source workspace.
   */
  private UUID getClonedWorkspaceId(
      FlightContext context, OperationType operationType, UUID workspaceUuid) {
    UUID subjectWorkspaceId = workspaceUuid;
    if (OperationType.CLONE == operationType) {
      // When the action is clone, the action clone is acted upon
      // the source workspace.
      subjectWorkspaceId =
          getRequired(
              context.getInputParameters(), ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    }
    return subjectWorkspaceId;
  }

  /**
   * Get the workspace where the activity happened. For cloning, it should be the destination
   * workspace.
   */
  private UUID getAffectedWorkspaceId(
      FlightContext context, OperationType operationType, UUID workspaceUuid) {
    // The workspace id that a db transaction has happened. In
    // the case of cloning, the db create a cloned resource in
    // the destination workspace.
    var affectedWorkspaceId = workspaceUuid;
    if (OperationType.CLONE == operationType) {
      affectedWorkspaceId =
          getRequired(
              context.getInputParameters(),
              ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
              UUID.class);
    }
    return affectedWorkspaceId;
  }

  private void logApplicationAbleFlight(
      UUID workspaceUuid,
      FlightContext context,
      String actorEmail,
      String actorSubjectId,
      OperationType operationType) {
    FlightUtils.validateRequiredEntries(context.getInputParameters(), APPLICATION_IDS);
    List<String> ids = context.getInputParameters().get(APPLICATION_IDS, new TypeReference<>() {});
    for (var id : ids) {
      activityLogDao.writeActivity(
          workspaceUuid,
          new DbWorkspaceActivityLog(
              actorEmail, actorSubjectId, operationType, id, ActivityLogChangedTarget.APPLICATION));
    }
  }

  private void maybeLogWorkspaceDeletionFlight(
      UUID workspaceUuid, String userEmail, String subjectId) {
    try {
      workspaceDao.getWorkspace(workspaceUuid);
      logger.warn(
          "Workspace {} is failed to be deleted; "
              + "not writing deletion to workspace activity log",
          workspaceUuid);
    } catch (WorkspaceNotFoundException e) {
      activityLogDao.writeActivity(
          workspaceUuid,
          new DbWorkspaceActivityLog(
              userEmail,
              subjectId,
              OperationType.DELETE,
              workspaceUuid.toString(),
              ActivityLogChangedTarget.WORKSPACE));
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
              ActivityLogChangedTarget.WORKSPACE));
    } else {
      logger.warn(
          "CloudContext in workspace {} deletion fails; not writing deletion "
              + "to workspace activity log",
          workspaceUuid);
    }
  }

  private void maybeLogFolderDeletionFlight(
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
              ActivityLogChangedTarget.FOLDER));
    }
  }

  private void maybeLogControlledResourcesDeletionFlight(
      FlightContext context, UUID workspaceUuid, String userEmail, String subjectId) {
    List<UUID> resourceIds = getControlledResourceToDeleteFromFlight(context);
    for (var resourceId : resourceIds) {
      try {
        resourceDao.getResource(workspaceUuid, resourceId);
        logger.warn(
            "Controlled resource {} in workspace {} is failed to be deleted; "
                + "not writing deletion to workspace activity log",
            resourceId,
            workspaceUuid);
      } catch (ResourceNotFoundException e) {
        activityLogDao.writeActivity(
            workspaceUuid,
            new DbWorkspaceActivityLog(
                userEmail,
                subjectId,
                OperationType.DELETE,
                resourceId.toString(),
                ActivityLogChangedTarget.RESOURCE));
      }
    }
  }

  private List<UUID> getControlledResourceToDeleteFromFlight(FlightContext context) {
    List<ControlledResource> controlledResource =
        checkNotNull(
            context
                .getInputParameters()
                .get(CONTROLLED_RESOURCES_TO_DELETE, new TypeReference<>() {}));
    return controlledResource.stream().map(WsmResource::getResourceId).toList();
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
              userEmail, subjectId, operationType, id, ActivityLogChangedTarget.WORKSPACE));
    }
  }

  private void maybeLogUpdateControlledResourceFieldsFlight(
      FlightContext context, OperationType operationType, String userEmail, String subjectId) {
    if (!context.getFlightStatus().equals(FlightStatus.SUCCESS)) {
      return;
    }
    FlightUtils.validateRequiredEntries(
        context.getWorkingMap(), CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP);

    Map<UUID, String> resourceIdToWorkspaceIdMap =
        Preconditions.checkNotNull(
            context
                .getWorkingMap()
                .get(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, new TypeReference<>() {}));

    for (Map.Entry<UUID, String> pair : resourceIdToWorkspaceIdMap.entrySet()) {
      activityLogDao.writeActivity(
          UUID.fromString(pair.getValue()),
          new DbWorkspaceActivityLog(
              userEmail,
              subjectId,
              operationType,
              pair.getKey().toString(),
              ActivityLogChangedTarget.RESOURCE));
    }
  }
}
