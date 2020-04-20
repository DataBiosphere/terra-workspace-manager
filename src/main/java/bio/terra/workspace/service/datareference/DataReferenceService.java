package bio.terra.workspace.service.datareference;

import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.FilterControlledEnum;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceService {

  private SamService samService;
  private DataReferenceDao dataReferenceDao;
  private WorkspaceDao workspaceDao;

  @Autowired
  public DataReferenceService(
      SamService samService, DataReferenceDao dataReferenceDao, WorkspaceDao workspaceDao) {
    this.samService = samService;
    this.dataReferenceDao = dataReferenceDao;
    this.workspaceDao = workspaceDao;
  }

  public DataReferenceList enumerateDataReferences(
      String workspaceId,
      int offset,
      int limit,
      FilterControlledEnum filterControlled,
      AuthenticatedUserRequest userReq) {
    if (!samService.isAuthorized(
        userReq.getRequiredToken(),
        SamUtils.SAM_WORKSPACE_RESOURCE,
        workspaceId,
        SamUtils.SAM_WORKSPACE_READ_ACTION)) {
      throw new SamUnauthorizedException(
          "User " + userReq.getEmail() + " is not authorized to read workspace " + workspaceId);
    }
    // TODO: do we want to give users a different error when the specified workspace doesn't exist?
    //  It requires an additional DB lookup (as an empty enumerate doesn't tell you whether
    //  or not a workspace exists), but it's a nicer user experience for catching small mistakes.
    if (!workspaceDao.workspaceExists(workspaceId)) {
      throw new WorkspaceNotFoundException("No workspace exists with ID " + workspaceId);
    }

    return dataReferenceDao.enumerateDataReferences(
        workspaceId, userReq.getReqId().toString(), offset, limit, filterControlled);
  }
}
