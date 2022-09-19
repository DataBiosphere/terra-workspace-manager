package bio.terra.workspace.common.utils;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultBigQueryDatasetCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultGcsBucketCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGrantRoleRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.springframework.http.MediaType;
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
public class MockMvcUtils {

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

  public static final String GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/buckets/generateName";
  public static final String GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/generateName";
  public static final String GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances/generateName";
  public static final String FOLDERS_V1_PATH_FORMAT = "/api/workspaces/v1/%s/folders";
  public static final String FOLDER_V1_PATH_FORMAT = "/api/workspaces/v1/%s/folders/%s";
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

  public static ApiCreatedWorkspace createWorkspace(MockMvc mockMvc, ObjectMapper objectMapper)
      throws Exception {
    var createRequest = WorkspaceFixtures.createWorkspaceRequestBody();
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH)
                            .content(objectMapper.writeValueAsString(createRequest)),
                        USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  public static ApiCreatedControlledGcpBigQueryDataset createBigQueryDataset(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      UUID workspaceId,
      AuthenticatedUserRequest userRequest)
      throws Exception {
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

  public static ApiGcpBigQueryDatasetResource getBigQueryDataset(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      UUID workspaceId,
      UUID resourceId,
      AuthenticatedUserRequest userRequest)
      throws Exception {
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

  public static ApiCreatedControlledGcpGcsBucket createGcsBucket(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      UUID workspaceId,
      AuthenticatedUserRequest userRequest)
      throws Exception {
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

    return objectMapper.readValue(
        serializedGetResponse, ApiCreatedControlledGcpGcsBucket.class);
  }

  public static ApiGcpGcsBucketResource getGcsBucket(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      UUID workspaceId,
      UUID resourceId,
      AuthenticatedUserRequest userRequest)
      throws Exception {
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

  public static void grantRole(
      UUID workspaceId,
      WsmIamRole role,
      String memberEmail,
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      AuthenticatedUserRequest userRequest)
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

  public static void deleteWorkspace(
      UUID workspaceId, MockMvc mockMvc, AuthenticatedUserRequest userRequest) throws Exception {
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
}
