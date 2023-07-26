package bio.terra.workspace.common.mocks;

import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceRequest;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiCreateCloudContextResult;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiPropertyKeys;
import bio.terra.workspace.generated.model.ApiUpdateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Component
public class MockWorkspaceV1Api {
  private static final Logger logger = LoggerFactory.getLogger(MockWorkspaceV1Api.class);

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
    createCloudContextAndWait(userRequest, createdWorkspace.getId(), apiCloudPlatform);
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
    createCloudContextAndWait(userRequest, resultWorkspaceId, apiCloudPlatform);
    return resultWorkspaceId;
  }

  public void deleteWorkspace(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    mockMvc
        .perform(addAuth(delete(String.format(WORKSPACES_V1, workspaceId)), userRequest))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
    mockMvc
        .perform(addAuth(get(String.format(WORKSPACES_V1, workspaceId)), userRequest))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  public int deleteWorkspaceIfExists(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(addAuth(delete(String.format(WORKSPACES_V1, workspaceId)), userRequest))
            .andReturn();
    return mvcResult.getResponse().getStatus();
  }

  public ApiWorkspaceDescription getWorkspace(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGet(userRequest, WORKSPACES_V1, workspaceId);
    return objectMapper.readValue(serializedResponse, ApiWorkspaceDescription.class);
  }

  public ApiWorkspaceDescription updateWorkspace(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable String newUserFacingId,
      @Nullable String newDisplayName,
      @Nullable String newDescription)
      throws Exception {
    ApiUpdateWorkspaceRequestBody requestBody = new ApiUpdateWorkspaceRequestBody();
    if (newUserFacingId != null) {
      requestBody.userFacingId(newUserFacingId);
    }
    if (newDisplayName != null) {
      requestBody.displayName(newDisplayName);
    }
    if (newDescription != null) {
      requestBody.description(newDescription);
    }

    String serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(WORKSPACES_V1, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(requestBody)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWorkspaceDescription.class);
  }

  public ApiCloneWorkspaceResult cloneWorkspace(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      String spendProfile,
      @Nullable ApiWsmPolicyInputs policiesToAdd,
      @Nullable UUID destinationWorkspaceId)
      throws Exception {
    ApiCloneWorkspaceRequest request =
        new ApiCloneWorkspaceRequest()
            .destinationWorkspaceId(destinationWorkspaceId)
            .spendProfile(spendProfile)
            .additionalPolicies(policiesToAdd);
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            WORKSPACES_V1_CLONE,
            sourceWorkspaceId,
            objectMapper.writeValueAsString(request));
    ApiCloneWorkspaceResult cloneWorkspace =
        objectMapper.readValue(serializedResponse, ApiCloneWorkspaceResult.class);
    if (destinationWorkspaceId == null) {
      destinationWorkspaceId = cloneWorkspace.getWorkspace().getDestinationWorkspaceId();
    }

    // Wait for the clone to complete
    String jobId = cloneWorkspace.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(cloneWorkspace.getJobReport())) {
      TimeUnit.SECONDS.sleep(5);
      serializedResponse =
          mockMvcUtils.getSerializedResponseForGetJobResult(
              userRequest, WORKSPACES_V1_CLONE_RESULT, destinationWorkspaceId, jobId);
      cloneWorkspace = objectMapper.readValue(serializedResponse, ApiCloneWorkspaceResult.class);
    }
    assertEquals(StatusEnum.SUCCEEDED, cloneWorkspace.getJobReport().getStatus());

    return cloneWorkspace;
  }

  // Cloud context

  public static final String CLOUD_CONTEXTS_V1_CREATE = WORKSPACES_V1 + "/cloudcontexts";
  public static final String CLOUD_CONTEXTS_V1_CREATE_RESULT =
      CLOUD_CONTEXTS_V1_CREATE + "/result/%s";

  private ApiCreateCloudContextResult createCloudContext(
      AuthenticatedUserRequest userRequest, UUID workspaceId, ApiCloudPlatform apiCloudPlatform)
      throws Exception {
    String jobId = UUID.randomUUID().toString();
    ApiCreateCloudContextRequest request =
        new ApiCreateCloudContextRequest()
            .cloudPlatform(apiCloudPlatform)
            .jobControl(new ApiJobControl().id(jobId));
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            CLOUD_CONTEXTS_V1_CREATE,
            workspaceId,
            objectMapper.writeValueAsString(request));
    return objectMapper.readValue(serializedResponse, ApiCreateCloudContextResult.class);
  }

  public void createCloudContextAndWait(
      AuthenticatedUserRequest userRequest, UUID workspaceId, ApiCloudPlatform apiCloudPlatform)
      throws Exception {
    ApiCreateCloudContextResult result =
        createCloudContext(userRequest, workspaceId, apiCloudPlatform);
    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(15);
      result = createCloudContextResult(userRequest, workspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());

    if (Objects.requireNonNull(apiCloudPlatform) == ApiCloudPlatform.GCP) {
      logger.info(
          "Created project %s for workspace %s"
              .formatted(result.getGcpContext().getProjectId(), workspaceId));
    }
  }

  private ApiCreateCloudContextResult createCloudContextResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGetJobResult(
            userRequest, CLOUD_CONTEXTS_V1_CREATE_RESULT, workspaceId, jobId);
    return objectMapper.readValue(serializedResponse, ApiCreateCloudContextResult.class);
  }

  public void deleteCloudContext(
      AuthenticatedUserRequest userRequest, UUID workspaceId, CloudPlatform cloudPlatform)
      throws Exception {
    String path = CLOUD_CONTEXTS_V1_CREATE + "/" + cloudPlatform.toString();
    if (cloudPlatform == CloudPlatform.GCP) {
      mockMvc
          .perform(addAuth(delete(path.formatted(workspaceId)), userRequest))
          .andExpect(status().isNoContent());
    }
  }

  // Properties

  public static final String WORKSPACES_V1_PROPERTIES = WORKSPACES_V1 + "/properties";

  public void updateWorkspaceProperties(
      AuthenticatedUserRequest userRequest, UUID workspaceId, List<ApiProperty> properties)
      throws Exception {
    mockMvcUtils.getSerializedResponseForPost(
        userRequest,
        WORKSPACES_V1_PROPERTIES,
        workspaceId,
        objectMapper.writeValueAsString(properties));
  }

  public void deleteWorkspaceProperties(
      AuthenticatedUserRequest userRequest, UUID workspaceId, List<String> propertyKeys)
      throws Exception {
    ApiPropertyKeys apiPropertyKeys = new ApiPropertyKeys();
    apiPropertyKeys.addAll(propertyKeys);
    mockMvc
        .perform(
            addAuth(
                patch(String.format(WORKSPACES_V1_PROPERTIES, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(apiPropertyKeys)),
                userRequest))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
  }

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
