package bio.terra.workspace.common.mocks;

import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

@Component
public class MockWorkspaceV1Api {

  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private ObjectMapper objectMapper;

  // Workspace

  public static final String WORKSPACES_V1_CREATE = "/api/workspaces/v1";
  public static final String WORKSPACES_V1 = WORKSPACES_V1_CREATE + "/%s";
  public static final String WORKSPACES_V1_BY_UFID =
      WORKSPACES_V1_CREATE + "/workspaceByUserFacingId/%s";
  public static final String WORKSPACES_V1_CLONE = WORKSPACES_V1 + "/clone";
  public static final String WORKSPACES_V1_CLONE_RESULT = WORKSPACES_V1 + "/clone-result/%s";

  public ApiCreatedWorkspace createWorkspaceWithoutCloudContext(
      @Nullable AuthenticatedUserRequest userRequest) throws Exception {
    return createWorkspaceWithoutCloudContext(userRequest, ApiWorkspaceStageModel.MC_WORKSPACE);
  }

  public ApiCreatedWorkspace createWorkspaceWithoutCloudContext(
      @Nullable AuthenticatedUserRequest userRequest, ApiWorkspaceStageModel stageModel)
      throws Exception {
    ApiCreateWorkspaceRequestBody request =
        WorkspaceFixtures.createWorkspaceRequestBody(stageModel);
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest, WORKSPACES_V1_CREATE, objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  public ApiCreatedWorkspace createWorkspaceWithoutCloudContext(
      @Nullable AuthenticatedUserRequest userRequest, ApiCreateWorkspaceRequestBody request)
      throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest, WORKSPACES_V1_CREATE, objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  public ApiErrorReport createWorkspaceWithoutCloudContextExpectError(
      @Nullable AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable ApiWorkspaceStageModel stageModel,
      @Nullable ApiWsmPolicyInputs policyInputs,
      int expectedCode)
      throws Exception {
    ApiCreateWorkspaceRequestBody request =
        WorkspaceFixtures.createWorkspaceRequestBody().id(workspaceId);
    if (stageModel != null) {
      request.stage(stageModel);
    }
    if (policyInputs != null) {
      request.policies(policyInputs);
    }
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_CREATE)
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(expectedCode))
            .andReturn()
            .getResponse()
            .getContentAsString();
    try {
      ApiErrorReport errorReport = objectMapper.readValue(serializedResponse, ApiErrorReport.class);
      assertEquals(expectedCode, errorReport.getStatusCode());
      return errorReport;
    } catch (Exception e) {
      // There is no ApiErrorReport to return
      return null;
    }
  }

  public ApiCreatedWorkspace createWorkspaceWithCloudContext(
      AuthenticatedUserRequest userRequest, ApiCloudPlatform apiCloudPlatform) throws Exception {
    ApiCreatedWorkspace createdWorkspace = createWorkspaceWithoutCloudContext(userRequest);
    mockMvcUtils.createCloudContextAndWait(userRequest, createdWorkspace.getId(), apiCloudPlatform);
    return createdWorkspace;
  }

  public ApiCreatedWorkspace createWorkspaceWithPolicy(
      AuthenticatedUserRequest userRequest, ApiWsmPolicyInputs policy) throws Exception {
    ApiCreateWorkspaceRequestBody request =
        WorkspaceFixtures.createWorkspaceRequestBody().policies(policy);

    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_CREATE)
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  public UUID createWorkspaceWithRegionConstraint(
      AuthenticatedUserRequest userRequest, String regionName) throws Exception {
    ApiWsmPolicyInputs regionPolicy =
        new ApiWsmPolicyInputs()
            .addInputsItem(
                new ApiWsmPolicyInput()
                    .namespace(PolicyFixtures.NAMESPACE)
                    .name(PolicyFixtures.REGION_CONSTRAINT)
                    .addAdditionalDataItem(
                        new ApiWsmPolicyPair().key(PolicyFixtures.REGION).value(regionName)));
    ApiCreatedWorkspace workspace = createWorkspaceWithPolicy(userRequest, regionPolicy);
    return workspace.getId();
  }

  public UUID createWorkspaceWithGroupConstraint(
      AuthenticatedUserRequest userRequest, String groupName) throws Exception {
    ApiWsmPolicyInputs groupPolicy =
        new ApiWsmPolicyInputs()
            .addInputsItem(
                new ApiWsmPolicyInput()
                    .namespace(PolicyFixtures.NAMESPACE)
                    .name(PolicyFixtures.GROUP_CONSTRAINT)
                    .addAdditionalDataItem(
                        new ApiWsmPolicyPair().key(PolicyFixtures.GROUP).value(groupName)));
    ApiCreatedWorkspace workspace = createWorkspaceWithPolicy(userRequest, groupPolicy);
    return workspace.getId();
  }

  public UUID createWorkspaceWithRegionConstraintAndCloudContext(
      AuthenticatedUserRequest userRequest, ApiCloudPlatform apiCloudPlatform, String regionName)
      throws Exception {
    UUID resultWorkspaceId = createWorkspaceWithRegionConstraint(userRequest, regionName);
    mockMvcUtils.createCloudContextAndWait(userRequest, resultWorkspaceId, apiCloudPlatform);
    return resultWorkspaceId;
  }

  // Cloud context

  public static final String CLOUD_CONTEXTS_V1 = WORKSPACES_V1 + "/cloudcontexts";
  public static final String CLOUD_CONTEXT_V2_CREATE_RESULT = CLOUD_CONTEXTS_V1 + "/result/%s";

  // Properties

  public static final String WORKSPACES_V1_PROPERTIES = WORKSPACES_V1 + "/properties";

  // Policies & region

  public static final String WORKSPACES_V1_POLICIES = WORKSPACES_V1 + "/policies";
  public static final String WORKSPACES_V1_POLICIES_EXPLAIN = WORKSPACES_V1_POLICIES + "/explain";
  public static final String WORKSPACES_V1_POLICIES_MERGE_CHECK =
      WORKSPACES_V1_POLICIES + "/mergeCheck";
  public static final String WORKSPACES_V1_LIST_VALID_REGIONS = WORKSPACES_V1 + "/listValidRegions";

  // Roles

  public static final String WORKSPACES_V1_GRANT_ROLE = WORKSPACES_V1 + "/roles/%s/members";
  public static final String WORKSPACES_V1_REMOVE_ROLE = WORKSPACES_V1_GRANT_ROLE + "/%s";

  // Resources

  public static final String WORKSPACES_V1_RESOURCES = WORKSPACES_V1 + "/resources";
  public static final String WORKSPACES_V1_RESOURCES_PROPERTIES =
      WORKSPACES_V1_RESOURCES + "/%s/properties";
}
