package bio.terra.workspace.service.workspace.get;

import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GetService {
  private final WorkspaceDao workspaceDao;
  private final SamService samService;

  @Autowired
  public GetService(WorkspaceDao workspaceDao, SamService samService) {
    this.workspaceDao = workspaceDao;
    this.samService = samService;
  }

  public WorkspaceDescription getWorkspace(String id, AuthenticatedUserRequest userReq) {

    try {
      samService.isAuthorized(
          userReq.getRequiredToken(),
          SamUtils.SAM_WORKSPACE_RESOURCE,
          id,
          SamUtils.SAM_WORKSPACE_READ_ACTION);
    } catch (ApiException samEx) {
      throw new SamApiException(samEx);
    }

    WorkspaceDescription result = workspaceDao.getWorkspace(id);
    return result;
  }
}
