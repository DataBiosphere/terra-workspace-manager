package bio.terra.workspace.common.utils;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultBigQueryDatasetCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultGcsBucketCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiCreateCloudContextResult;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGrantRoleRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A collection of utilities and constants useful for MockMVC-based tests. This style of tests lets
 * us test controller-layer code (request/response parsing, authz, and validation) without actually
 * spinning up a local server.
 *
 * <p>TODO: it's probably worth looking into whether we can automatically pull routes from the
 * generated swagger, instead of manually wrapping them here.
 */
@Component
public class MockMvcUtils {
  private static final Logger logger = LoggerFactory.getLogger(MockMvcUtils.class);

  // Do not Autowire UserAccessUtils. UserAccessUtils are for connected tests and not unit tests
  // (since unit tests don't use real SAM). Instead, each method must take in userRequest.
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  public static final String AUTH_HEADER = "Authorization";

  public static final String WORKSPACES_V1_PATH = "/api/workspaces/v1";
  public static final String WORKSPACES_V1_BY_UUID_PATH_FORMAT = "/api/workspaces/v1/%s";
  public static final String WORKSPACES_V1_BY_UFID_PATH_FORMAT =
      "/api/workspaces/v1/workspaceByUserFacingId/%s";
  public static final String ADD_USER_TO_WORKSPACE_PATH_FORMAT =
      "/api/workspaces/v1/%s/roles/%s/members";
  public static final String CLONE_WORKSPACE_PATH_FORMAT = "/api/workspaces/v1/%s/clone";
  public static final String UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT =
      "/api/workspaces/v1/%s/properties";
  public static final String GRANT_ROLE_PATH_FORMAT = "/api/workspaces/v1/%s/roles/%s/members";
  public static final String CREATE_SNAPSHOT_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots";
  public static final String CREATE_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts";
  public static final String GET_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts/result/%s";

  public static final String CREATE_AZURE_IP_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/ip";
  public static final String CREATE_AZURE_DISK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/disks";
  public static final String CREATE_AZURE_NETWORK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/network";
  public static final String CREATE_AZURE_VM_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/vm";
  public static final String CLONE_CONTROLLED_GCP_GCS_BUCKET_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/%s/clone";
  public static final String CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKET_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/clone-result/%s";
  public static final String GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/generateName";
  public static final String GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/generateName";
  public static final String GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances/generateName";
  public static final String FOLDERS_V1_PATH_FORMAT = "/api/workspaces/v1/%s/folders";
  public static final String FOLDER_V1_PATH_FORMAT = "/api/workspaces/v1/%s/folders/%s";
  public static final String FOLDER_PROPERTIES_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/folders/%s/properties";
  public static final String RESOURCE_PROPERTIES_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/%s/properties";
  public static final String CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets";
  public static final String CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/%s";
  public static final String CONTROLLED_GCP_GCS_BUCKETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets";
  public static final String CONTROLLED_GCP_GCS_BUCKET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/%s";
  public static final String REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots";
  public static final String REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/buckets";
  public static final String REFERENCED_GCP_GCS_OBJECTS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bucket/objects";
  public static final String REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatasets";
  public static final String REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatatables";
  public static final String REFERENCED_GIT_REPO_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gitrepos";
  public static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  public static MockHttpServletRequestBuilder addAuth(
      MockHttpServletRequestBuilder request, AuthenticatedUserRequest userRequest) {
    return request.header(AUTH_HEADER, "Bearer " + userRequest.getRequiredToken());
  }

  public static MockHttpServletRequestBuilder addJsonContentType(
      MockHttpServletRequestBuilder request) {
    return request.contentType("application/json");
  }

  public ApiWorkspaceDescription getWorkspace(AuthenticatedUserRequest userRequest, UUID id)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, id));
    String serializedResponse =
        mockMvc
            .perform(addAuth(requestBuilder, userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiWorkspaceDescription.class);
  }

  public ApiWorkspaceDescription createWorkspaceWithCloudContext(
      AuthenticatedUserRequest userRequest) throws Exception {
    ApiWorkspaceDescription createdWorkspace = createWorkspaceWithoutCloudContext(userRequest);
    createGcpCloudContextAndWait(userRequest, createdWorkspace.getId());
    return createdWorkspace;
  }

  public ApiWorkspaceDescription createWorkspaceWithoutCloudContext(
      @Nullable AuthenticatedUserRequest userRequest) throws Exception {
    ApiCreateWorkspaceRequestBody request = WorkspaceFixtures.createWorkspaceRequestBody();
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH).content(objectMapper.writeValueAsString(request)),
                        Optional.ofNullable(userRequest).orElse(USER_REQUEST))))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Return ApiWorkspaceDescription instead of ApiCreatedWorkspace, since former has
    // getUserFacingId().
    UUID workspaceId =
        objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class).getId();
    return getWorkspace(userRequest, workspaceId);
  }

  private void createGcpCloudContextAndWait(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    ApiCreateCloudContextResult result = createGcpCloudContext(userRequest, workspaceId);
    UUID jobId = UUID.fromString(result.getJobReport().getId());
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result = getCreateCloudContextResult(userRequest, workspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Created project %s for workspace %s"
            .formatted(result.getGcpContext().getProjectId(), workspaceId));
  }

  private ApiCreateCloudContextResult createGcpCloudContext(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    String jobId = UUID.randomUUID().toString();
    ApiCreateCloudContextRequest request =
        new ApiCreateCloudContextRequest()
            .cloudPlatform(ApiCloudPlatform.GCP)
            .jobControl(new ApiJobControl().id(jobId));
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(CREATE_CLOUD_CONTEXT_PATH_FORMAT.formatted(workspaceId.toString()))
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(HttpStatus.SC_ACCEPTED))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCreateCloudContextResult.class);
  }

  private ApiCreateCloudContextResult getCreateCloudContextResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID jobId) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        get(
                            GET_CLOUD_CONTEXT_PATH_FORMAT.formatted(
                                workspaceId.toString(), jobId.toString())),
                        userRequest)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCreateCloudContextResult.class);
  }

  public void deleteWorkspace(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    mockMvc
        .perform(
            addAuth(
                delete(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceId)), userRequest))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceId)), userRequest))
        .andExpect(status().is(HttpStatus.SC_NOT_FOUND));
  }

  public ApiCreatedControlledGcpBigQueryDataset createBigQueryDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    ApiCreateControlledGcpBigQueryDatasetRequestBody datasetCreationRequest =
        new ApiCreateControlledGcpBigQueryDatasetRequestBody()
            .common(makeDefaultControlledResourceFieldsApi())
            .dataset(defaultBigQueryDatasetCreationParameters());

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(
                            CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT,
                            workspaceId.toString()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(datasetCreationRequest)),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readValue(
        serializedGetResponse, ApiCreatedControlledGcpBigQueryDataset.class);
  }

  public ApiGcpBigQueryDatasetResource getBigQueryDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    get(
                        String.format(
                            CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT,
                            workspaceId.toString(),
                            resourceId.toString())),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readValue(serializedGetResponse, ApiGcpBigQueryDatasetResource.class);
  }

  public ApiCreatedControlledGcpGcsBucket createGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    ApiCreateControlledGcpGcsBucketRequestBody gcsBucketCreationRequest =
        new ApiCreateControlledGcpGcsBucketRequestBody()
            .common(makeDefaultControlledResourceFieldsApi())
            .gcsBucket(defaultGcsBucketCreationParameters());

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(
                            CONTROLLED_GCP_GCS_BUCKETS_V1_PATH_FORMAT, workspaceId.toString()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(gcsBucketCreationRequest)),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readValue(serializedGetResponse, ApiCreatedControlledGcpGcsBucket.class);
  }

  public ApiGcpGcsBucketResource getGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    get(
                        String.format(
                            CONTROLLED_GCP_GCS_BUCKET_V1_PATH_FORMAT,
                            workspaceId.toString(),
                            resourceId.toString())),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readValue(serializedGetResponse, ApiGcpGcsBucketResource.class);
  }

  public void grantRole(
      AuthenticatedUserRequest userRequest, UUID workspaceId, WsmIamRole role, String memberEmail)
      throws Exception {
    var requestBody = new ApiGrantRoleRequestBody().memberEmail(memberEmail);
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(GRANT_ROLE_PATH_FORMAT, workspaceId, role.name()))
                        .content(objectMapper.writeValueAsString(requestBody)),
                    userRequest)))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
  }
}
