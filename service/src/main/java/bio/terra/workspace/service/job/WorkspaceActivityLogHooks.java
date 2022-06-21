package bio.terra.workspace.service.job;

import static com.google.common.base.Preconditions.checkNotNull;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
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
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkspaceActivityLogHooks implements StairwayHook {
  private static final Logger logger = LoggerFactory.getLogger(StairwayHook.class);

  private final WorkspaceActivityLogDao activityLogDao;
  private final WorkspaceDao workspaceDao;
  private final ResourceDao resourceDao;

  private static final String DELETE_GCP_CONTEXT_FLIGHT = DeleteGcpContextFlight.class.getName();
  private static final String DELETE_AZURE_CONTEXT_FLIGHT =
      DeleteAzureContextFlight.class.getName();

  private static final String DELETE_CONTROLLED_RESOURCE_FLIGHT =
      DeleteControlledResourceFlight.class.getName();

  public WorkspaceActivityLogHooks(
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
    UUID workspaceUuid = UUID.fromString(workspaceId);
    if (context.getFlightStatus() == FlightStatus.SUCCESS) {
      activityLogDao.writeActivity(
          workspaceUuid, new DbWorkspaceActivityLog().operationType(operationType));
      return HookAction.CONTINUE;
    }
    // For DELETE flight, even if the flight failed, database deletion cannot be undone, so it's
    // still deleted from the database and we should log that as a changed activity.
    if (operationType == OperationType.DELETE) {
      if (isWorkspaceDeleted(workspaceUuid)) {
        activityLogDao.writeActivity(
            workspaceUuid, new DbWorkspaceActivityLog().operationType(operationType));
        return HookAction.CONTINUE;
      }
      if (isCloudContextDeleted(context, workspaceUuid)) {
        activityLogDao.writeActivity(
            workspaceUuid, new DbWorkspaceActivityLog().operationType(operationType));
        return HookAction.CONTINUE;
      }
      if (isControlledResourceDeleted(context, workspaceUuid)) {
        activityLogDao.writeActivity(
            workspaceUuid, new DbWorkspaceActivityLog().operationType(operationType));
        return HookAction.CONTINUE;
      }
    }
    return HookAction.CONTINUE;
  }

  private boolean isWorkspaceDeleted(UUID workspaceUuid) {
    try {
      workspaceDao.getWorkspace(workspaceUuid);
    } catch (WorkspaceNotFoundException e) {
      return true;
    }
    return false;
  }

  private boolean isCloudContextDeleted(FlightContext context, UUID workspaceUuid) {
    Optional<String> cloudContext;
    if (context.getFlightClassName().equals(DELETE_AZURE_CONTEXT_FLIGHT)) {
      cloudContext = workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.AZURE);
    } else if (context.getFlightClassName().equals(DELETE_GCP_CONTEXT_FLIGHT)) {
      cloudContext = workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP);
    } else {
      // Not a cloud context deletion flight.
      return false;
    }
    return cloudContext.isEmpty();
  }

  private boolean isControlledResourceDeleted(FlightContext context, UUID workspaceUuid) {
    if (!context.getFlightClassName().equals(DELETE_CONTROLLED_RESOURCE_FLIGHT)) {
      return false;
    }
    var controlledResource =
        checkNotNull(
            context.getInputParameters().get(ResourceKeys.RESOURCE, ControlledResource.class));
    UUID resourceId = controlledResource.getResourceId();
    try {
      resourceDao.getResource(workspaceUuid, resourceId);
    } catch (ResourceNotFoundException e) {
      return true;
    }
    return false;
  }
}
