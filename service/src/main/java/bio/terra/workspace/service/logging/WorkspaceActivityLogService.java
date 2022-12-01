package bio.terra.workspace.service.logging;

import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WsmObjectType;
import java.util.Optional;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.springframework.stereotype.Component;

/** This component is a service for logging workspace activities. */
@Component
public class WorkspaceActivityLogService {

  private final SamService samService;
  private final WorkspaceActivityLogDao workspaceActivityLogDao;

  public WorkspaceActivityLogService(
      SamService samService, WorkspaceActivityLogDao workspaceActivityLogDao) {
    this.samService = samService;
    this.workspaceActivityLogDao = workspaceActivityLogDao;
  }

  /** Writes the change activity. */
  public void writeActivity(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      OperationType operationType,
      String changeSubjectId,
      WsmObjectType objectType) {
    UserStatusInfo userStatusInfo =
        SamRethrow.onInterrupted(
            () -> samService.getUserStatusInfo(userRequest), "Get user status info from SAM");
    workspaceActivityLogDao.writeActivity(
        workspaceUuid,
        new DbWorkspaceActivityLog(
            // Use the userEmail from UserStatusInfo instead of userRequest because the email
            // in userRequest could be the pet SA email.
            userStatusInfo.getUserEmail(),
            userStatusInfo.getUserSubjectId(),
            operationType,
            changeSubjectId,
            objectType));
  }

  public Optional<ActivityLogChangeDetails> getLastUpdatedDetails(UUID workspaceId) {
    return workspaceActivityLogDao.getLastUpdateDetails(workspaceId);
  }
}
