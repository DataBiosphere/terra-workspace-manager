package bio.terra.workspace.service.stage;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.exceptions.StageDisabledException;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A small service to validate whether a workspace is at a particular stage. Separating it out
 * addresses circular references.
 */
@Component
public class StageService {
  private final WorkspaceDao workspaceDao;

  @Autowired
  public StageService(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  /**
   * Given a workspace object, error if it is not an MC workspace
   *
   * @param workspace workspace object
   * @param actionMessage message to include in the error
   */
  public void assertMcWorkspace(Workspace workspace, String actionMessage) {
    if (!WorkspaceStage.MC_WORKSPACE.equals(workspace.getWorkspaceStage())) {
      throw new StageDisabledException(
          workspace.getWorkspaceId(), workspace.getWorkspaceStage(), actionMessage);
    }
  }

  /**
   * Given a workspaceId, read the workspace info from the database and then validate that it is an
   * MC workspace.
   *
   * @param workspaceId workspace unique id
   * @param actionMessage message to include in the error
   */
  public void assertMcWorkspace(UUID workspaceId, String actionMessage) {
    try {
      Workspace workspace = workspaceDao.getWorkspace(workspaceId);
      assertMcWorkspace(workspace, actionMessage);
    } catch (InterruptedException e) {
      throw new InternalServerErrorException("Interrupted during assertMcWorkspace");
    }
  }
}
