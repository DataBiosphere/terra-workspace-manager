package bio.terra.workspace.service.logging;

import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceActivityLogService {

  private final SamService samService;
  private final WorkspaceActivityLogDao workspaceActivityLogDao;

  public WorkspaceActivityLogService(
      SamService samService, WorkspaceActivityLogDao workspaceActivityLogDao) {
    this.samService = samService;
    this.workspaceActivityLogDao = workspaceActivityLogDao;
  }

  public void writeActivity(
      AuthenticatedUserRequest userRequest, UUID workspaceUuid, OperationType operationType) {
    UserStatusInfo userStatusInfo =
        SamRethrow.onInterrupted(
            () -> samService.getUserStatusInfo(userRequest), "Get user status info from SAM");
    ;
    workspaceActivityLogDao.writeActivity(
        workspaceUuid,
        new DbWorkspaceActivityLog()
            .operationType(operationType)
            .actorEmail(userStatusInfo.getUserEmail())
            .actorSubjectId(userStatusInfo.getUserSubjectId()));
  }
}
