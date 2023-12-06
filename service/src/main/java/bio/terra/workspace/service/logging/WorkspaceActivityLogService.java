package bio.terra.workspace.service.logging;

import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.model.OperationType;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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

  @WithSpan
  // Writes the change activity
  public void writeActivity(
      AuthenticatedUserRequest userRequest,
      UUID workspaceUuid,
      OperationType operationType,
      String changeSubjectId,
      ActivityLogChangedTarget objectType) {
    UserStatusInfo userStatusInfo =
        Rethrow.onInterrupted(
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
    return workspaceActivityLogDao.getLastUpdatedDetails(workspaceId);
  }

  public Optional<ActivityLogChangeDetails> getLastUpdatedDetails(
      UUID workspaceId, String changeSubjectId) {
    return workspaceActivityLogDao.getLastUpdatedDetails(workspaceId, changeSubjectId);
  }
}
