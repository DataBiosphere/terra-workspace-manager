package bio.terra.workspace.common.utils;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultBigQueryDatasetCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultGcsBucketCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultNotebookCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetResult;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceRequest;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiCreateCloudContextResult;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGrantRoleRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.generated.model.ApiResourceDescription;
import bio.terra.workspace.generated.model.ApiResourceList;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsUpdateMode;
import bio.terra.workspace.generated.model.ApiUpdateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CompleteTableCopyJobsStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.CreateTableCopyJobsStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.RetrieveBigQueryDatasetCloudAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.SetReferencedDestinationBigQueryDatasetInWorkingMapStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.dataset.SetReferencedDestinationBigQueryDatasetResponseStep;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceMetadataStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
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
  public static final String AUTH_HEADER = "Authorization";
  public static final String WORKSPACES_V1_PATH = "/api/workspaces/v1";
  public static final String WORKSPACES_V1_BY_UUID_PATH_FORMAT = "/api/workspaces/v1/%s";
  public static final String WORKSPACES_V1_BY_UFID_PATH_FORMAT =
      "/api/workspaces/v1/workspaceByUserFacingId/%s";
  public static final String ADD_USER_TO_WORKSPACE_PATH_FORMAT =
      "/api/workspaces/v1/%s/roles/%s/members";
  public static final String CLONE_WORKSPACE_PATH_FORMAT = "/api/workspaces/v1/%s/clone";
  public static final String CLONE_WORKSPACE_RESULT_PATH_FORMAT =
      "/api/workspaces/v1/%s/clone-result/%s";
  public static final String UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT =
      "/api/workspaces/v1/%s/properties";
  public static final String UPDATE_WORKSPACES_V1_POLICIES_PATH_FORMAT =
      "/api/workspaces/v1/%S/policies";
  public static final String GRANT_ROLE_PATH_FORMAT = "/api/workspaces/v1/%s/roles/%s/members";
  public static final String REMOVE_ROLE_PATH_FORMAT = "/api/workspaces/v1/%s/roles/%s/members/%s";
  public static final String RESOURCES_PATH_FORMAT = "/api/workspaces/v1/%s/resources";
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
  public static final String CREATE_AZURE_SAS_TOKEN_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/storageContainer/%s/getSasToken";
  public static final String GET_REFERENCED_GCP_GCS_BUCKET_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/buckets/%s";
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
  public static final String CONTROLLED_GCP_AI_NOTEBOOKS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances";
  public static final String CONTROLLED_GCP_AI_NOTEBOOKS_V1_RESULT_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/ai-notebook-instances/create-result/%s";
  public static final String CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets";
  public static final String CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/%s";
  public static final String CLONE_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/%s/clone";
  public static final String CLONE_RESULT_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/gcp/bqdatasets/clone-result/%s";
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
  public static final String REFERENCED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatasets";
  public static final String REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatasets/%s";
  public static final String REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gcp/bigquerydatatables";
  public static final String REFERENCED_GIT_REPO_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/gitrepos";
  public static final String DELETE_FOLDER_JOB_V1_PATH_FORMAT =
      "/api/workspaces/v1/%s/folders/%s/result/%s";
  public static final String UPDATE_POLICIES_PATH_FORMAT = "/api/workspaces/v1/%s/policies";

  // Only use this if you are mocking SAM. If you're using real SAM,
  // use userAccessUtils.defaultUserAuthRequest() instead.
  public static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));
  private static final Logger logger = LoggerFactory.getLogger(MockMvcUtils.class);
  private static final String DEST_BUCKET_RESOURCE_NAME =
      TestUtils.appendRandomNumber("i-am-the-cloned-bucket");

  // Do not Autowire UserAccessUtils. UserAccessUtils are for connected tests and not unit tests
  // (since unit tests don't use real SAM). Instead, each method must take in userRequest.
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired JobService jobService;
  @Autowired StairwayComponent stairwayComponent;

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

  public ApiCloneWorkspaceResult cloneWorkspace(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      String spendProfile,
      @Nullable UUID destinationWorkspaceId)
      throws Exception {

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(CLONE_WORKSPACE_PATH_FORMAT, sourceWorkspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(
                            objectMapper.writeValueAsString(
                                new ApiCloneWorkspaceRequest()
                                    .destinationWorkspaceId(destinationWorkspaceId)
                                    .spendProfile(spendProfile))),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_ACCEPTED))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiCloneWorkspaceResult cloneWorkspace =
        objectMapper.readValue(serializedGetResponse, ApiCloneWorkspaceResult.class);
    if (destinationWorkspaceId == null) {
      destinationWorkspaceId = cloneWorkspace.getWorkspace().getDestinationWorkspaceId();
    }

    // Wait for the clone to complete
    String jobId = cloneWorkspace.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(cloneWorkspace.getJobReport())) {
      TimeUnit.SECONDS.sleep(5);
      cloneWorkspace = getCloneWorkspaceResult(USER_REQUEST, destinationWorkspaceId, jobId);
    }
    assertEquals(ApiJobReport.StatusEnum.SUCCEEDED, cloneWorkspace.getJobReport().getStatus());

    return cloneWorkspace;
  }

  public ApiCreatedWorkspace createWorkspaceWithCloudContext(AuthenticatedUserRequest userRequest)
      throws Exception {
    ApiCreatedWorkspace createdWorkspace = createWorkspaceWithoutCloudContext(userRequest);
    createGcpCloudContextAndWait(userRequest, createdWorkspace.getId());
    return createdWorkspace;
  }

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
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH).content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  public ApiErrorReport createWorkspaceWithoutCloudContextExpectError(
      @Nullable AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable ApiWorkspaceStageModel stageModel,
      @Nullable ApiTpsPolicyInputs policyInputs,
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
                        post(WORKSPACES_V1_PATH).content(objectMapper.writeValueAsString(request)),
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

  public ApiCloneWorkspaceResult getCloneWorkspaceResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        get(
                            CLONE_WORKSPACE_RESULT_PATH_FORMAT.formatted(
                                workspaceId.toString(), jobId)),
                        userRequest)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCloneWorkspaceResult.class);
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
                    patch(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceId))
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

  public void updateWorkspaceProperties(
      AuthenticatedUserRequest userRequest, UUID workspaceId, List<ApiProperty> properties)
      throws Exception {
    mockMvc
        .perform(
            addAuth(
                post(String.format(UPDATE_WORKSPACES_V1_PROPERTIES_PATH_FORMAT, workspaceId))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(objectMapper.writeValueAsString(properties)),
                userRequest))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
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

  public ApiTpsPaoUpdateResult updatePolicies(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      @Nullable List<ApiTpsPolicyInput> policiesToAdd,
      @Nullable List<ApiTpsPolicyInput> policiesToRemove)
      throws Exception {
    ApiTpsPaoUpdateRequest requestBody =
        new ApiTpsPaoUpdateRequest().updateMode(ApiTpsUpdateMode.FAIL_ON_CONFLICT);
    if (policiesToAdd != null) {
      requestBody.addAttributes(new ApiTpsPolicyInputs().inputs(policiesToAdd));
    }
    if (policiesToRemove != null) {
      requestBody.removeAttributes(new ApiTpsPolicyInputs().inputs(policiesToRemove));
    }
    String serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    patch(String.format(UPDATE_POLICIES_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(requestBody)),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiTpsPaoUpdateResult.class);
  }

  public void deletePolicies(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    ApiWorkspaceDescription workspace = getWorkspace(userRequest, workspaceId);
    updatePolicies(
        userRequest,
        workspaceId,
        /*policiesToAdd=*/ null,
        /*policiesToRemove=*/ workspace.getPolicies());
  }

  public void assertWorkspace(
      ApiWorkspaceDescription actualWorkspace,
      String expectedUserFacingId,
      String expectedDisplayName,
      String expectedDescription,
      String expectedCreatedByEmail,
      String expectedLastUpdatedByEmail) {
    assertEquals(expectedUserFacingId, actualWorkspace.getUserFacingId());
    assertEquals(expectedDisplayName, actualWorkspace.getDisplayName());
    assertEquals(expectedDescription, actualWorkspace.getDescription());
    OffsetDateTime firstLastUpdatedDate = actualWorkspace.getLastUpdatedDate();
    assertNotNull(firstLastUpdatedDate);
    OffsetDateTime createdDate = actualWorkspace.getCreatedDate();
    assertNotNull(createdDate);
    assertTrue(firstLastUpdatedDate.isAfter(createdDate));
    assertEquals(expectedCreatedByEmail, actualWorkspace.getCreatedBy());
    assertEquals(expectedLastUpdatedByEmail, actualWorkspace.getLastUpdatedBy());
  }

  public ApiCreatedControlledGcpAiNotebookInstanceResult createAiNotebookInstance(
      AuthenticatedUserRequest userRequest, UUID workspaceId, @Nullable String location)
      throws Exception {
    ApiCreateControlledGcpAiNotebookInstanceRequestBody notebookCreationRequest =
        new ApiCreateControlledGcpAiNotebookInstanceRequestBody()
            .common(
                makeDefaultControlledResourceFieldsApi()
                    .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE.toApiModel())
                    .name(TestUtils.appendRandomNumber("ai-notebook")))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()))
            .aiNotebookInstance(
                defaultNotebookCreationParameters()
                    .location(location)
                    .instanceId(TestUtils.appendRandomNumber("instance-id")));

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(
                            CONTROLLED_GCP_AI_NOTEBOOKS_V1_PATH_FORMAT, workspaceId.toString()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(notebookCreationRequest)),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_ACCEPTED))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        objectMapper.readValue(
            serializedGetResponse, ApiCreatedControlledGcpAiNotebookInstanceResult.class);
    UUID jobId = UUID.fromString(result.getJobReport().getId());
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result = getAiNotebookInstanceResult(userRequest, workspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());

    return result;
  }

  public ApiCreatedControlledGcpAiNotebookInstanceResult getAiNotebookInstanceResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID jobId) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        get(
                            CONTROLLED_GCP_AI_NOTEBOOKS_V1_RESULT_PATH_FORMAT.formatted(
                                workspaceId.toString(), jobId.toString())),
                        userRequest)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(
        serializedResponse, ApiCreatedControlledGcpAiNotebookInstanceResult.class);
  }

  public ApiCreatedControlledGcpBigQueryDataset createControlledBqDataset(
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

  public ApiGcpBigQueryDatasetResource getControlledBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    return getBqDataset(
        userRequest, workspaceId, resourceId, CONTROLLED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT);
  }

  public ApiGcpBigQueryDatasetResource getReferencedBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    return getBqDataset(
        userRequest, workspaceId, resourceId, REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT);
  }

  private ApiGcpBigQueryDatasetResource getBqDataset(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId, String path)
      throws Exception {
    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(path, workspaceId.toString(), resourceId.toString())),
                    userRequest))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readValue(serializedGetResponse, ApiGcpBigQueryDatasetResource.class);
  }

  public ApiGcpBigQueryDatasetResource cloneControlledBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName)
      throws Exception {
    return cloneControlledBqDataset(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        destDatasetName,
        /*location=*/ null);
  }

  /** Call cloneBigQueryDataset() and wait for flight to finish. */
  public ApiGcpBigQueryDatasetResource cloneControlledBqDataset(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName,
      @Nullable String destLocation)
      throws Exception {
    ApiCloneControlledGcpBigQueryDatasetResult result =
        cloneControlledBqDatasetAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            destDatasetName,
            destLocation,
            HttpStatus.SC_ACCEPTED,
            /*shouldUndo=*/ false);
    UUID jobId = UUID.fromString(result.getJobReport().getId());
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result = getCloneControlledBqDatasetResult(userRequest, sourceWorkspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Controlled BQ dataset clone of resource %s completed. ".formatted(sourceResourceId));
    return result.getDataset().getDataset();
  }

  /** Call cloneBigQueryDataset(), wait for flight to finish, return JobError. */
  public ApiErrorReport cloneControlledBqDataset_jobError(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName,
      int expectedCode)
      throws Exception {
    ApiCloneControlledGcpBigQueryDatasetResult result =
        cloneControlledBqDatasetAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            destDatasetName,
            HttpStatus.SC_ACCEPTED,
            /*shouldUndo=*/ false);
    return cloneControlledBqDataset_waitForJobError(
        userRequest,
        sourceWorkspaceId,
        UUID.fromString(result.getJobReport().getId()),
        expectedCode);
  }

  public void cloneControlledBqDataset_undo(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      String destResourceName,
      ApiCloningInstructionsEnum cloningInstructions)
      throws Exception {
    ApiCloneControlledGcpBigQueryDatasetResult result =
        cloneControlledBqDatasetAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            destResourceName,
            /*destDatasetName=*/ null,
            HttpStatus.SC_ACCEPTED,
            /*shouldUndo=*/ true);
    cloneControlledBqDataset_waitForJobError(
        userRequest,
        sourceWorkspaceId,
        UUID.fromString(result.getJobReport().getId()),
        HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  /** Call cloneBigQueryDataset() and return immediately; don't wait for flight to finish. */
  public ApiCloneControlledGcpBigQueryDatasetResult cloneControlledBqDatasetAsync(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName,
      int expectedCode,
      boolean shouldUndo)
      throws Exception {
    return cloneControlledBqDatasetAsync(
        userRequest,
        sourceWorkspaceId,
        sourceResourceId,
        destWorkspaceId,
        cloningInstructions,
        destResourceName,
        destDatasetName,
        /*location=*/ null,
        expectedCode,
        shouldUndo);
  }

  /** Call cloneBigQueryDataset() and return immediately; don't wait for flight to finish. */
  public ApiCloneControlledGcpBigQueryDatasetResult cloneControlledBqDatasetAsync(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      @Nullable String destResourceName,
      @Nullable String destDatasetName,
      @Nullable String destLocation,
      int expectedCode,
      boolean shouldUndo)
      throws Exception {
    // Retry to ensure steps are idempotent
    Map<String, StepStatus> retrySteps = new HashMap<>();
    List<Class> steps =
        ImmutableList.of(
            CheckControlledResourceAuthStep.class,
            RetrieveBigQueryDatasetCloudAttributesStep.class,
            SetReferencedDestinationBigQueryDatasetInWorkingMapStep.class,
            CreateReferenceMetadataStep.class,
            SetReferencedDestinationBigQueryDatasetResponseStep.class,
            CreateTableCopyJobsStep.class,
            CompleteTableCopyJobsStep.class);
    steps.forEach(step -> retrySteps.put(step.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY));
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder()
            .doStepFailures(retrySteps)
            .lastStepFailure(shouldUndo)
            .build());

    ApiCloneControlledGcpBigQueryDatasetRequest request =
        new ApiCloneControlledGcpBigQueryDatasetRequest()
            .destinationWorkspaceId(destWorkspaceId)
            .cloningInstructions(cloningInstructions)
            .location(destLocation)
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    if (!StringUtils.isEmpty(destResourceName)) {
      request.name(destResourceName);
    }
    if (!StringUtils.isEmpty(destDatasetName)) {
      request.destinationDatasetName(destDatasetName);
    }

    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(CLONE_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT.formatted(
                                sourceWorkspaceId, sourceResourceId))
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(expectedCode))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // If an exception was thrown, deserialization won't work, so don't attempt it.
    return expectedCode == HttpStatus.SC_ACCEPTED
        ? objectMapper.readValue(
            serializedResponse, ApiCloneControlledGcpBigQueryDatasetResult.class)
        : null;
  }

  private ApiErrorReport cloneControlledBqDataset_waitForJobError(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID jobId, int expectedCode)
      throws Exception {
    // While job is running, cloneBigQueryDataset returns ApiCloneControlledGcpBigQueryDatasetResult
    // After job fails, cloneBigQueryData returns ApiCloneControlledGcpBigQueryDatasetResult OR
    // ApiErrorReport.
    ApiCloneControlledGcpBigQueryDatasetResult result =
        getCloneControlledBqDatasetResult(userRequest, workspaceId, jobId);
    ApiErrorReport errorReport = null;
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 3000);
      String serializedResponse =
          getCloneControlledBqDatasetResult_serializedResponse(userRequest, workspaceId, jobId);
      try {
        result =
            objectMapper.readValue(
                serializedResponse, ApiCloneControlledGcpBigQueryDatasetResult.class);
      } catch (UnrecognizedPropertyException e) {
        errorReport = objectMapper.readValue(serializedResponse, ApiErrorReport.class);
        assertEquals(expectedCode, errorReport.getStatusCode());
        return errorReport;
      }
    }
    // Job failed and cloneBigQueryData returned ApiCloneControlledGcpBigQueryDatasetResult
    assertEquals(StatusEnum.FAILED, result.getJobReport().getStatus());
    return result.getErrorReport();
  }

  private ApiCloneControlledGcpBigQueryDatasetResult getCloneControlledBqDatasetResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID jobId) throws Exception {
    String serializedResponse =
        getCloneControlledBqDatasetResult_serializedResponse(userRequest, workspaceId, jobId);
    return objectMapper.readValue(
        serializedResponse, ApiCloneControlledGcpBigQueryDatasetResult.class);
  }

  private String getCloneControlledBqDatasetResult_serializedResponse(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID jobId) throws Exception {
    return mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    get(
                        CLONE_RESULT_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT.formatted(
                            workspaceId.toString(), jobId.toString())),
                    userRequest)))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private ApiErrorReport getCloneControlledBqDatasetResult_jobError(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID jobId) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        get(
                            CLONE_RESULT_CONTROLLED_GCP_BIG_QUERY_DATASET_FORMAT.formatted(
                                workspaceId.toString(), jobId.toString())),
                        userRequest)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiErrorReport.class);
  }

  public ApiCreatedControlledGcpGcsBucket createControlledGcsBucket(
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

  public ApiGcpGcsBucketResource getControlledGcsBucket(
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

  /** Call cloneGcsBucket() and wait for flight to finish. */
  public ApiCloneControlledGcpGcsBucketResult cloneControlledGcsBucket(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions)
      throws Exception {
    ApiCloneControlledGcpGcsBucketResult result =
        cloneControlledGcsBucketAsync(
            userRequest,
            sourceWorkspaceId,
            sourceResourceId,
            destWorkspaceId,
            cloningInstructions,
            /*bucketName=*/ "",
            HttpStatus.SC_ACCEPTED);
    UUID jobId = UUID.fromString(result.getJobReport().getId());
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result = getCloneControlledGcsBucketResult(userRequest, destWorkspaceId, jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    logger.info(
        "Controlled GCS bucket clone of resource %s completed. ".formatted(sourceResourceId));
    return result;
  }

  /** Call cloneGcsBucket() and return immediately; don't wait for flight to finish. */
  public ApiCloneControlledGcpGcsBucketResult cloneControlledGcsBucketAsync(
      AuthenticatedUserRequest userRequest,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      UUID destWorkspaceId,
      ApiCloningInstructionsEnum cloningInstructions,
      String destBucketName,
      int expectedCode)
      throws Exception {
    ApiCloneControlledGcpGcsBucketRequest request =
        new ApiCloneControlledGcpGcsBucketRequest()
            .destinationWorkspaceId(destWorkspaceId)
            .cloningInstructions(cloningInstructions)
            .name(TestUtils.appendRandomNumber(DEST_BUCKET_RESOURCE_NAME))
            .jobControl(new ApiJobControl().id(UUID.randomUUID().toString()));
    if (destBucketName != "") {
      request.bucketName(destBucketName);
    }

    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(CLONE_CONTROLLED_GCP_GCS_BUCKET_FORMAT.formatted(
                                sourceWorkspaceId, sourceResourceId))
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andExpect(status().is(expectedCode))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // If an exception was thrown, deserialization won't work, so don't attempt it.
    return expectedCode == HttpStatus.SC_ACCEPTED
        ? objectMapper.readValue(serializedResponse, ApiCloneControlledGcpGcsBucketResult.class)
        : null;
  }

  private ApiCloneControlledGcpGcsBucketResult getCloneControlledGcsBucketResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID jobId) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        get(
                            CLONE_RESULT_CONTROLLED_GCP_GCS_BUCKET_FORMAT.formatted(
                                workspaceId.toString(), jobId.toString())),
                        userRequest)))
            // Returns 200 if flight is done, 202 if flight is running.
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCloneControlledGcpGcsBucketResult.class);
  }

  public ApiGcpGcsBucketResource getReferencedGcsBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        get(
                            GET_REFERENCED_GCP_GCS_BUCKET_FORMAT.formatted(
                                workspaceId.toString(), resourceId.toString())),
                        userRequest)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
  }

  public ApiDataRepoSnapshotResource createDataRepoSnapshotReference(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {

    var datarepoSnapshotRequest =
        new ApiCreateDataRepoSnapshotReferenceRequestBody()
            .metadata(
                new ApiReferenceResourceCommonFields()
                    .cloningInstructions(ApiCloningInstructionsEnum.REFERENCE)
                    .description("description")
                    .name(RandomStringUtils.randomAlphabetic(10)))
            .snapshot(
                new ApiDataRepoSnapshotAttributes().instanceName("terra").snapshot("polaroid"));

    return createDataRepoSnapshotReference(userRequest, workspaceId, datarepoSnapshotRequest);
  }

  public ApiDataRepoSnapshotResource createDataRepoSnapshotReference(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      ApiCreateDataRepoSnapshotReferenceRequestBody datarepoSnapshotRequest)
      throws Exception {

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(
                            REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT, workspaceId.toString()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(datarepoSnapshotRequest)),
                    userRequest))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readValue(serializedGetResponse, ApiDataRepoSnapshotResource.class);
  }

  public List<ApiResourceDescription> enumerateResources(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        get(RESOURCES_PATH_FORMAT.formatted(workspaceId.toString())), userRequest)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiResourceList.class).getResources();
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

  public void removeRole(
      AuthenticatedUserRequest userRequest, UUID workspaceId, WsmIamRole role, String memberEmail)
      throws Exception {
    mockMvc
        .perform(
            addAuth(
                delete(
                    String.format(REMOVE_ROLE_PATH_FORMAT, workspaceId, role.name(), memberEmail)),
                userRequest))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT));
  }

  public void assertProperties(List<ApiProperty> expected, List<ApiProperty> actual) {
    assertThat(expected, containsInAnyOrder(actual.toArray()));
  }

  public ApiJobReport getJobReport(String path, AuthenticatedUserRequest userRequest)
      throws Exception {
    String serializedResponse =
        mockMvc
            .perform(addJsonContentType(addAuth(get(path), userRequest)))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiJobResult.class).getJobReport();
  }

  /** Posts http request and expect error thrown. */
  public void postExpect(String request, String api, int httpStatus) throws Exception {
    mockMvc
        .perform(
            addAuth(
                post(api)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(request),
                USER_REQUEST))
        .andExpect(status().is(httpStatus));
  }
}
