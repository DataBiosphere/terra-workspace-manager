package bio.terra.workspace.common.fixtures;

import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.Properties;
import com.google.common.collect.ImmutableList;
import java.util.UUID;

public class WorkspaceV2Fixtures {

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

  // @Autowired private WorkspaceService workspaceService;
  // @Autowired private final UserAccessUtils userAccessUtils;

  //  @Autowired MvcWorkspaceApi mvcWorkspaceApi;

  /* public WorkspaceV2Fixtures(UserAccessUtils userAccessUtils) {
    this.userAccessUtils = userAccessUtils;
  }*/

  public static String getUserFacingId(UUID workspaceId) {
    return String.format("user-facing-id-%s", workspaceId);
  }

  public static ApiCreateWorkspaceV2Request createWorkspaceV2Request(
      ApiWorkspaceStageModel stageModel) {
    UUID workspaceId = UUID.randomUUID();
    ApiProperties properties = new ApiProperties();
    properties.addAll(
        ImmutableList.of(
            TYPE_PROPERTY, SHORT_DESCRIPTION_PROPERTY, VERSION_PROPERTY, USER_SET_PROPERTY));
    return new ApiCreateWorkspaceV2Request()
        .id(workspaceId)
        .userFacingId(getUserFacingId(workspaceId))
        .displayName(WORKSPACE_NAME)
        .description("A test workspace created by createWorkspaceRequestBody")
        .stage(stageModel)
        .properties(properties)
        .cloudPlatform(ApiCloudPlatform.AWS)
        .spendProfile(DEFAULT_SPEND_PROFILE)
        .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
  }
  /*
   public  Workspace createWorkspaceV2(CloudPlatform cloudPlatform) throws Exception {
     ApiCloudPlatform apiCloudPlatform = (cloudPlatform != null) ? cloudPlatform.toApiModel() : null;

     ApiCreateWorkspaceV2Result result =
         mvcWorkspaceApi.createWorkspaceAndWait(userAccessUtils.defaultUser().getAuthenticatedRequest(), apiCloudPlatform);

     return
         workspaceService.getWorkspace(result.getWorkspaceId());
   }

  */

}
