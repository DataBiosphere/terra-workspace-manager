package bio.terra.workspace.common.fixtures;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
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
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.spendprofile.model.SpendProfileOrganization;
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

  public static final UUID WORKSPACE_ID = UUID.fromString("00000000-fcf0-4981-bb96-6b8dd634e7c0");
  public static final String WORKSPACE_NAME = "TestWorkspace";
  public static final ApiProperty TYPE_PROPERTY =
      new ApiProperty().key(Properties.TYPE).value("type");
  public static final ApiProperty SHORT_DESCRIPTION_PROPERTY =
      new ApiProperty().key(Properties.SHORT_DESCRIPTION).value("short description");
  public static final ApiProperty VERSION_PROPERTY =
      new ApiProperty().key(Properties.VERSION).value("version 3");
  public static final ApiProperty USER_SET_PROPERTY =
      new ApiProperty().key("userkey").value("uservalue");
  public static final String DEFAULT_GCP_SPEND_PROFILE_NAME = "wm-default-spend-profile";
  public static final SpendProfileId DEFAULT_GCP_SPEND_PROFILE_ID =
      new SpendProfileId(DEFAULT_GCP_SPEND_PROFILE_NAME);
  public static final SpendProfile DEFAULT_GCP_SPEND_PROFILE =
      SpendProfile.buildGcpSpendProfile(DEFAULT_GCP_SPEND_PROFILE_ID, "billingAccountId");
  public static final String DEFAULT_AZURE_SPEND_PROFILE_NAME = "facade00-0000-4000-a000-000000000000";
  public static final SpendProfileId DEFAULT_AZURE_SPEND_PROFILE_ID =
          new SpendProfileId(DEFAULT_AZURE_SPEND_PROFILE_NAME);
//  public static final SpendProfile DEFAULT_AZURE_SPEND_PROFILE =
//          SpendProfile.buildAzureSpendProfile(DEFAULT_AZURE_SPEND_PROFILE_ID, UUID.fromString("decade00-0000-4000-a000-000000000000"), UUID.fromString("5ca1ab1e-0000-4000-a000-000000000000"), "default-MRG", new SpendProfileOrganization(false, ));

  public static final String DEFAULT_USER_EMAIL = "fake@gmail.com";
  public static final String DEFAULT_USER_SUBJECT_ID = "subjectId123456";
  public static final SamUser SAM_USER =
      new SamUser(DEFAULT_USER_EMAIL, DEFAULT_USER_SUBJECT_ID, new BearerToken("token"));
  public static final ApiException API_EXCEPTION = new ApiException("error");
  public static final NotFoundException NOT_FOUND_EXCEPTION = new NotFoundException("not found");
  public static final UnauthorizedException UNAUTHORIZED_EXCEPTION =
      new UnauthorizedException("unauthorized");

  /**
   * Generate the request body for creating an MC_WORKSPACE stage workspace.
   *
   * <p>All values are mutable, and tests should change any they explicitly need.
   */
  public static ApiCreateWorkspaceRequestBody createWorkspaceRequestBody() {
    return createWorkspaceRequestBody(ApiWorkspaceStageModel.MC_WORKSPACE);
  }

  public static Workspace createDefaultMcWorkspace() {
    return createDefaultMcWorkspace(DEFAULT_GCP_SPEND_PROFILE_ID);
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
    workspaceDao.createWorkspaceStart(workspace, flightId);
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
        .spendProfile(DEFAULT_GCP_SPEND_PROFILE_NAME)
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
