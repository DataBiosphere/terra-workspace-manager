package bio.terra.workspace.unit;

import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;

/** Utilities for working with workspaces in unit tests. */
public class WorkspaceUnitTestUtils {
  public static final String PROJECT_ID = "my-project-id";
  public static final SpendProfileId SPEND_PROFILE_ID = new SpendProfileId("my-spend-profile");
  public static final String POLICY_OWNER = "policy-owner";
  public static final String POLICY_WRITER = "policy-writer";
  public static final String POLICY_READER = "policy-reader";
  public static final String POLICY_APPLICATION = "policy-application";

  /**
   * Creates a workspaces with a GCP cloud context and stores it in the database. Returns the
   * workspace id.
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
    String flightId = UUID.randomUUID().toString();
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    workspaceDao.createWorkspaceStart(workspace, /* applicationIds= */ null, flightId);
    workspaceDao.createWorkspaceSuccess(workspace.workspaceId(), flightId);
    return workspace.getWorkspaceId();
  }

  /**
   * Creates the database artifact for a GCP cloud context without actually creating anything beyond
   * the database row.
   */
  public static void createGcpCloudContextInDatabase(
      WorkspaceDao workspaceDao, UUID workspaceUuid, String projectId) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.GCP, SPEND_PROFILE_ID, flightId);
    workspaceDao.createCloudContextSuccess(
        workspaceUuid,
        CloudPlatform.GCP,
        new GcpCloudContext(
                projectId, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION, null)
            .serialize(),
        flightId);
  }

  public static void deleteGcpCloudContextInDatabase(
      WorkspaceDao workspaceDao, UUID workspaceUuid) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.deleteCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);
    workspaceDao.deleteCloudContextSuccess(workspaceUuid, CloudPlatform.GCP, flightId);
  }

  private WorkspaceUnitTestUtils() {}
}
