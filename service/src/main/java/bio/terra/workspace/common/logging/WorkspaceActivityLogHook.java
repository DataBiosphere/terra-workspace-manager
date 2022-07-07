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
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.DeleteAzureContextFlight;
import bio.terra.workspace.service.workspace.flight.DeleteGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceDeleteFlight;
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

  // TODO(PF-1800): instead of storing the flight name here, have an ActivityFlight enum for
  // each flight and iterate through them and log different activity change details
  // for different Flights.
  private static final String DELETE_WORKSPACE_FLIGHT = WorkspaceDeleteFlight.class.getName();
  private static final String DELETE_GCP_CONTEXT_FLIGHT = DeleteGcpContextFlight.class.getName();
  private static final String DELETE_AZURE_CONTEXT_FLIGHT =
      DeleteAzureContextFlight.class.getName();

  private static final String DELETE_CONTROLLED_RESOURCE_FLIGHT =
      DeleteControlledResourceFlight.class.getName();

  @Autowired
  public WorkspaceActivityLogHook(
      WorkspaceActivityLogDao activityLogDao, WorkspaceDao workspaceDao, ResourceDao resourceDao) {
    this.activityLogDao = activityLogDao;
    this.workspaceDao = workspaceDao;
    this.resourceDao = resourceDao;
  }

  @Override
  public HookAction endFlight(FlightContext context) {
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
    ActivityFlight af = ActivityFlight.fromFlightClassName(context.getFlightClassName());
    UUID workspaceUuid = UUID.fromString(workspaceId);
    if (context.getFlightStatus() == FlightStatus.SUCCESS) {
      activityLogDao.writeActivity(
          workspaceUuid, new DbWorkspaceActivityLog().operationType(operationType));
      return HookAction.CONTINUE;
    }
    if (operationType != OperationType.DELETE) {
      return HookAction.CONTINUE;
    }
    // If DELETE flight failed, cloud resource may or may not have been deleted. Check if cloud
    // resource was deleted. If so, write to activity log.
    switch (af.getActivityLogChangedTarget()) {
      case WORKSPACE -> maybeLogWorkspaceDeletionFlight(workspaceUuid);
      case AZURE_CLOUD_CONTEXT -> maybeLogCloudContextDeletionFlight(
          CloudPlatform.AZURE, workspaceUuid);
      case GCP_CLOUD_CONTEXT -> maybeLogCloudContextDeletionFlight(
          CloudPlatform.GCP, workspaceUuid);
      case RESOURCE -> maybeLogControlledResourceDeletion(context, workspaceUuid);
      default -> throw new UnhandledDeletionFlightException(
          String.format(
              "Activity log should be updated for deletion flight %s failures",
              context.getFlightClassName()));
    }
    return HookAction.CONTINUE;
  }

  private void maybeLogWorkspaceDeletionFlight(UUID workspaceUuid) {
    try {
      workspaceDao.getWorkspace(workspaceUuid);
      logger.warn(
          String.format(
              "Workspace %s is failed to be deleted; "
                  + "not writing deletion to workspace activity log",
              workspaceUuid));
    } catch (WorkspaceNotFoundException e) {
      activityLogDao.writeActivity(
          workspaceUuid, new DbWorkspaceActivityLog().operationType(OperationType.DELETE));
    }
  }

  private void maybeLogCloudContextDeletionFlight(CloudPlatform cloudPlatform, UUID workspaceUuid) {
    Optional<String> cloudContext = workspaceDao.getCloudContext(workspaceUuid, cloudPlatform);
    if (cloudContext.isEmpty()) {
      activityLogDao.writeActivity(
          workspaceUuid, new DbWorkspaceActivityLog().operationType(OperationType.DELETE));
    } else {
      logger.warn(
          String.format(
              "CloudContext in workspace %s deletion fails; not writing deletion "
                  + "to workspace activity log",
              workspaceUuid));
    }
  }

  private void maybeLogControlledResourceDeletion(FlightContext context, UUID workspaceUuid) {
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
          workspaceUuid, new DbWorkspaceActivityLog().operationType(OperationType.DELETE));
    }
  }
}
