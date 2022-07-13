package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
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

  /**
   * Generate the request body for creating an MC_WORKSPACE stage workspace.
   *
   * <p>All values are mutable, and tests should change any they explicitly need.
   */
  public static ApiCreateWorkspaceRequestBody createWorkspaceRequestBody() {
    UUID workspaceId = UUID.randomUUID();
    ApiProperties properties = new ApiProperties();
    ApiProperty property1 = new ApiProperty();
    property1.setKey("foo");
    property1.setValue("bar");

    ApiProperty property2 = new ApiProperty();
    property2.setKey("xyzzy");
    property2.setValue("plohg");

    properties.add(property1);
    properties.add(property2);

    return new ApiCreateWorkspaceRequestBody()
        .id(workspaceId)
        .displayName("TestWorkspace")
        .description("A test workspace created by createWorkspaceRequestBody")
        .userFacingId(getUserFacingId(workspaceId))
        .stage(ApiWorkspaceStageModel.MC_WORKSPACE)
        .spendProfile(SamResource.SPEND_PROFILE)
        .properties(properties);
  }

  public static String getUserFacingId(UUID workspaceId) {
    return String.format("user-facing-id-%s", workspaceId);
  }
}
