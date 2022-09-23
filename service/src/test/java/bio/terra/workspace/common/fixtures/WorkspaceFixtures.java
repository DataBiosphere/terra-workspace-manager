package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.Properties;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableList;
import java.util.UUID;

public class WorkspaceFixtures {

  public static final String WORKSPACE_NAME = "TestWorkspace";
  public static final ApiProperty TYPE_PROPERTY =
      new ApiProperty().key(Properties.TYPE).value("type");
  public static final ApiProperty SHORT_DESCRIPTION_PROPERTY =
      new ApiProperty().key(Properties.SHORT_DESCRIPTION).value("short description");
  public static final ApiProperty VERSION_PROPERTY =
      new ApiProperty().key(Properties.VERSION).value("version 3");
  public static final ApiProperty USER_SET_PROPERTY =
      new ApiProperty().key("userkey").value("uservalue");

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

  /**
   * Creates a workspaces with a GCP cloud context and stores it in the database. Returns the
   * workspace id.
   *
   * <p>The {@link ResourceDao#createControlledResource(ControlledResource)} checks that a relevant
   * cloud context exists before storing the resource.
   */
  public static UUID createGcpWorkspace(WorkspaceDao workspaceDao) {
    UUID workspaceId = createWorkspace(workspaceDao);
    WorkspaceFixtures.createGcpCloudContextInDatabase(workspaceDao, workspaceId, "my-project-id");
    return workspaceId;
  }

  /**
   * Creates a workspaces without a cloud context and stores it in the database. Returns the
   * workspace id.
   */
  public static UUID createWorkspace(WorkspaceDao workspaceDao) {
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
   * Generate the request body for creating an MC_WORKSPACE stage workspace.
   *
   * <p>All values are mutable, and tests should change any they explicitly need.
   */
  public static ApiCreateWorkspaceRequestBody createWorkspaceRequestBody() {
    UUID workspaceId = UUID.randomUUID();
    ApiProperties properties = new ApiProperties();
    properties.addAll(
        ImmutableList.of(
            TYPE_PROPERTY, SHORT_DESCRIPTION_PROPERTY, VERSION_PROPERTY, USER_SET_PROPERTY));
    return new ApiCreateWorkspaceRequestBody()
        .id(workspaceId)
        .displayName(WORKSPACE_NAME)
        .description("A test workspace created by createWorkspaceRequestBody")
        .userFacingId(getUserFacingId(workspaceId))
        .stage(ApiWorkspaceStageModel.MC_WORKSPACE)
        .spendProfile("wm-default-spend-profile")
        .properties(properties);
  }

  public static String getUserFacingId(UUID workspaceId) {
    return String.format("user-facing-id-%s", workspaceId);
  }
}
