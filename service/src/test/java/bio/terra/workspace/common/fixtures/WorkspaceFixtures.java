package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.common.fixtures.PolicyFixtures.DEFAULT_WSM_POLICY_INPUTS;
import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;

import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.Properties;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;

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

  public static final String DEFAULT_SPEND_PROFILE = "wm-default-spend-profile";

  /**
   * Generate the request body for creating an MC_WORKSPACE stage workspace.
   *
   * <p>All values are mutable, and tests should change any they explicitly need.
   */
  public static ApiCreateWorkspaceRequestBody createWorkspaceRequestBody() {
    return createWorkspaceRequestBody(ApiWorkspaceStageModel.MC_WORKSPACE);
  }

  public static Workspace createDefaultMcWorkspace() {
    return new Workspace(
        UUID.randomUUID(),
        RandomStringUtils.randomAlphabetic(10).toLowerCase(Locale.ROOT),
        "default workspace",
        "this is an awesome workspace",
        new SpendProfileId("default-spend"),
        Collections.emptyMap(),
        WorkspaceStage.MC_WORKSPACE,
        DEFAULT_USER_EMAIL,
        null);
  }

  public static Workspace buildWorkspace(
      @Nullable UUID workspaceUuid, WorkspaceStage workspaceStage) {
    return defaultWorkspaceBuilder(workspaceUuid).workspaceStage(workspaceStage).build();
  }

  public static Workspace buildMcWorkspace() {
    return buildMcWorkspace(null);
  }

  public static Workspace buildMcWorkspace(@Nullable UUID workspaceUuid) {
    return buildWorkspace(workspaceUuid, WorkspaceStage.MC_WORKSPACE);
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
        .workspaceStage(WorkspaceStage.MC_WORKSPACE)
        .createdByEmail(DEFAULT_USER_EMAIL);
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
        .spendProfile(DEFAULT_SPEND_PROFILE)
        .properties(properties)
        .policies(DEFAULT_WSM_POLICY_INPUTS);
  }

  public static String getUserFacingId(UUID workspaceId) {
    return String.format("user-facing-id-%s", workspaceId);
  }
}
