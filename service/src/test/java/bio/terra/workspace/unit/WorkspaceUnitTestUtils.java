package bio.terra.workspace.unit;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;

/** Utilities for working with workspaces in unit tests. */
public class WorkspaceUnitTestUtils {
  public static final String PROJECT_ID = "my-project-id";

  /**
   * Creates a workspaces with a GCP cloud context and stores it in the database. Returns the
   * workspace id.
   *
   * <p>The {@link ResourceDao#createControlledResource(ControlledResource)} checks that a relevant
   * cloud context exists before storing the resource.
   */
  public static UUID createWorkspaceWithGcpContext(WorkspaceDao workspaceDao) {
    UUID workspaceId = createWorkspaceWithoutGcpContext(workspaceDao);
    createGcpCloudContextInDatabase(workspaceDao, workspaceId, PROJECT_ID);
    return workspaceId;
  }

  /**
   * Creates a workspaces without a cloud context and stores it in the database. Returns the
   * workspace id.
   */
  public static UUID createWorkspaceWithoutGcpContext(WorkspaceDao workspaceDao) {
    UUID workspaceUuid = UUID.randomUUID();
    Workspace workspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId(workspaceUuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(workspace);
    return workspace.getWorkspaceId();
  }

  /**
   * Creates the database artifact for a GCP cloud context without actually creating anything beyond
   * the database row.
   */
  public static void createGcpCloudContextInDatabase(
      WorkspaceDao workspaceDao, UUID workspaceUuid, String projectId) {
    createCloudContextInDatabase(workspaceDao, workspaceUuid, projectId, CloudPlatform.GCP);
  }

  /**
   * Creates the database artifact for a cloud context without actually creating anything beyond the
   * database row.
   */
  public static void createCloudContextInDatabase(
      WorkspaceDao workspaceDao,
      UUID workspaceUuid,
      String projectId,
      CloudPlatform cloudPlatform) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceUuid, cloudPlatform, flightId);
    workspaceDao.createCloudContextFinish(
        workspaceUuid, cloudPlatform, new GcpCloudContext(projectId).serialize(), flightId);
  }

  private WorkspaceUnitTestUtils() {}
}
