package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
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
  public static final SpendProfileId DEFAULT_SPEND_PROFILE_ID =
      new SpendProfileId(DEFAULT_SPEND_PROFILE);

  public static final SamUser SAM_USER =
      new SamUser("example@example.com", "123ABC", new BearerToken("token"));

  /**
   * Generate the request body for creating an MC_WORKSPACE stage workspace.
   *
   * <p>All values are mutable, and tests should change any they explicitly need.
   */
  public static ApiCreateWorkspaceRequestBody createWorkspaceRequestBody() {
    return createWorkspaceRequestBody(ApiWorkspaceStageModel.MC_WORKSPACE);
  }

  public static Workspace createDefaultMcWorkspace() {
    return createDefaultMcWorkspace(DEFAULT_SPEND_PROFILE_ID);
  }

  public static Workspace createDefaultMcWorkspace(SpendProfileId spendProfileId) {
    return new Workspace(
        UUID.randomUUID(),
        RandomStringUtils.randomAlphabetic(10).toLowerCase(Locale.ROOT),
        "default workspace",
        "this is an awesome workspace",
        spendProfileId,
        Collections.emptyMap(),
        WorkspaceStage.MC_WORKSPACE,
        DEFAULT_USER_EMAIL,
        null,
        null,
        null,
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

  public static void createWorkspaceInDb(Workspace workspace, WorkspaceDao workspaceDao) {
    var flightId = UUID.randomUUID().toString();
    workspaceDao.createWorkspaceStart(workspace, /* applicationIds */ null, flightId);
    workspaceDao.createWorkspaceSuccess(workspace.workspaceId(), flightId);
  }

  public static boolean deleteWorkspaceFromDb(UUID workspaceUuid, WorkspaceDao workspaceDao) {
    var flightId = UUID.randomUUID().toString();
    workspaceDao.deleteWorkspaceStart(workspaceUuid, flightId);
    return workspaceDao.deleteWorkspaceSuccess(workspaceUuid, flightId);
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
        .properties(properties);
  }

  public static FlightMap createCloudContextInputs(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      CloudPlatform cloudPlatform,
      SpendProfile spendProfile) {
    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(WorkspaceFlightMapKeys.CLOUD_PLATFORM, cloudPlatform);
    inputs.put(WorkspaceFlightMapKeys.SPEND_PROFILE, spendProfile);
    return inputs;
  }

  public static FlightMap deleteCloudContextInputs(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest, CloudPlatform cloudPlatform) {
    FlightMap inputs = new FlightMap();
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    inputs.put(WorkspaceFlightMapKeys.CLOUD_PLATFORM, cloudPlatform);
    return inputs;
  }

  public static String getUserFacingId(UUID workspaceId) {
    return String.format("user-facing-id-%s", workspaceId);
  }
}
