package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import java.util.UUID;

public class WorkspaceFixtures {

  public static void createGcpCloudContext(
      WorkspaceDao workspaceDao, UUID workspaceId, String projectId) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceId, CloudPlatform.GCP, flightId);
    workspaceDao.createCloudContextFinish(
        workspaceId, CloudPlatform.GCP, new GcpCloudContext(projectId).serialize(), flightId);
  }
}
