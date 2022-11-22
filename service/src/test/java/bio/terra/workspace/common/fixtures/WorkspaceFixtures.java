package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.Properties;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

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
   * Generate the request body for creating an MC_WORKSPACE stage workspace.
   *
   * <p>All values are mutable, and tests should change any they explicitly need.
   */
  public static ApiCreateWorkspaceRequestBody createWorkspaceRequestBody() {
    return createWorkspaceRequestBody(ApiWorkspaceStageModel.MC_WORKSPACE);
  }

  public static Workspace createWorkspace(
      @Nullable UUID workspaceUuid, WorkspaceStage workspaceStage) {
    return defaultWorkspaceBuilder(workspaceUuid).workspaceStage(workspaceStage).build();
  }

  public static Workspace createMcWorkspace() {
    return createWorkspace(null, WorkspaceStage.MC_WORKSPACE);
  }

  /**
   * Convenience method for getting a WorkspaceRequest builder with some pre-filled default values.
   * Default to an MC workspace.
   *
   * <p>This provides default values for jobId (random UUID), spend profile (Optional.empty()), and
   * workspace stage (MC_WORKSPACE).
   *
   * @param workspaceUuid if null, a uuid will be generated as the workspace id.
   */
  public static Workspace.Builder defaultWorkspaceBuilder(@Nullable UUID workspaceUuid) {
    var id = Optional.ofNullable(workspaceUuid).orElse(UUID.randomUUID());
    return Workspace.builder()
        .workspaceId(id)
        .userFacingId("a" + id)
        .workspaceStage(WorkspaceStage.MC_WORKSPACE);
  }

  public static ApiCreateWorkspaceRequestBody createWorkspaceRequestBody(
      ApiWorkspaceStageModel stageModel) {
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
        .stage(stageModel)
        .spendProfile("wm-default-spend-profile")
        .properties(properties);
  }

  public static String getUserFacingId(UUID workspaceId) {
    return String.format("user-facing-id-%s", workspaceId);
  }
}
