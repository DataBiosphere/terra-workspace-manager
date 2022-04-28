package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;

public class WorkspaceFixtures {

  /**
   * This method creates the database artifact for a cloud context without actually creating
   * anything beyond the database row.
   *
   * @param workspaceDao workspace DAO for the creation
   * @param workspaceUuid fake workspaceUuid to connect the context to
   * @param projectId fake projectId to for the context
   */
  public static void createGcpCloudContextInDatabase(
      WorkspaceDao workspaceDao, UUID workspaceUuid, String projectId) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);
    workspaceDao.createCloudContextFinish(
        workspaceUuid, CloudPlatform.GCP, new GcpCloudContext(projectId).serialize(), flightId);
  }
}
