package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeBqDataTableReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeDataRepoSnapshotReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeGcpBqDatasetReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeGcsBucketReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeGcsObjectReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeGitRepoReferenceRequestBody;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_GCS_OBJECTS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GIT_REPO_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.createWorkspace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDataTableReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsBucketReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGcpGcsObjectReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateGitRepoReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectResource;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.iam.SamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class ReferencedGcpResourceControllerTest extends BaseUnitTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean SamService mockSamService;

  @BeforeEach
  public void setUp() throws InterruptedException {
    // Needed for workspace creation as logging is triggered when a workspace is created in
    // `WorkspaceActivityLogHook` where we extract the user request information and log it to
    // activity log.
    when(mockSamService.getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
  }

  @Test
  public void createDataRepoReferencedResource() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiCreateDataRepoSnapshotReferenceRequestBody requestBody =
        makeDataRepoSnapshotReferenceRequestBody();
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT);

    var retrievedResource =
        objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
    assertReferenceResourceCommonFields(requestBody.getMetadata(), retrievedResource.getMetadata());
    assertEquals(
        requestBody.getSnapshot().getSnapshot(), retrievedResource.getAttributes().getSnapshot());
    assertEquals(
        requestBody.getSnapshot().getInstanceName(),
        retrievedResource.getAttributes().getInstanceName());
  }

  @Test
  public void createGcsBucketReferencedResource() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiCreateGcpGcsBucketReferenceRequestBody requestBody = makeGcsBucketReferenceRequestBody();
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT);

    var retrievedResource =
        objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
    assertReferenceResourceCommonFields(requestBody.getMetadata(), retrievedResource.getMetadata());
    assertEquals(
        requestBody.getBucket().getBucketName(), retrievedResource.getAttributes().getBucketName());
  }

  @Test
  public void createGcsObjectReferencedResource() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiCreateGcpGcsObjectReferenceRequestBody requestBody = makeGcsObjectReferenceRequestBody();
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GCP_GCS_OBJECTS_V1_PATH_FORMAT);

    var retrievedResource =
        objectMapper.readValue(serializedResponse, ApiGcpGcsObjectResource.class);
    assertReferenceResourceCommonFields(requestBody.getMetadata(), retrievedResource.getMetadata());
    assertEquals(
        requestBody.getFile().getBucketName(), retrievedResource.getAttributes().getBucketName());
    assertEquals(
        requestBody.getFile().getFileName(), retrievedResource.getAttributes().getFileName());
  }

  @Test
  public void createBqDatasetReferencedResource() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiCreateGcpBigQueryDatasetReferenceRequestBody requestBody =
        makeGcpBqDatasetReferenceRequestBody();
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT);

    var retrievedResource =
        objectMapper.readValue(serializedResponse, ApiGcpBigQueryDatasetResource.class);
    assertReferenceResourceCommonFields(requestBody.getMetadata(), retrievedResource.getMetadata());
    assertEquals(
        requestBody.getDataset().getDatasetId(), retrievedResource.getAttributes().getDatasetId());
    assertEquals(
        requestBody.getDataset().getProjectId(), retrievedResource.getAttributes().getProjectId());
  }

  @Test
  public void createBqDatatableReferencedResource() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiCreateGcpBigQueryDataTableReferenceRequestBody requestBody =
        makeBqDataTableReferenceRequestBody();
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT);

    var retrievedResource =
        objectMapper.readValue(serializedResponse, ApiGcpBigQueryDataTableResource.class);
    assertReferenceResourceCommonFields(requestBody.getMetadata(), retrievedResource.getMetadata());
    assertEquals(
        requestBody.getDataTable().getDataTableId(),
        retrievedResource.getAttributes().getDataTableId());
    assertEquals(
        requestBody.getDataTable().getDatasetId(),
        retrievedResource.getAttributes().getDatasetId());
    assertEquals(
        requestBody.getDataTable().getProjectId(),
        retrievedResource.getAttributes().getProjectId());
  }

  @Test
  public void createGitRepoReferencedResource() throws Exception {
    UUID workspaceId = createWorkspace(mockMvc, objectMapper).getId();
    ApiCreateGitRepoReferenceRequestBody requestBody = makeGitRepoReferenceRequestBody();
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GIT_REPO_V1_PATH_FORMAT);

    var retrievedResource = objectMapper.readValue(serializedResponse, ApiGitRepoResource.class);
    assertReferenceResourceCommonFields(requestBody.getMetadata(), retrievedResource.getMetadata());
    assertEquals(
        requestBody.getGitrepo().getGitRepoUrl(),
        retrievedResource.getAttributes().getGitRepoUrl());
  }

  private String createReferencedResourceAndGetSerializedResponse(
      UUID workspaceId, String request, String apiFormat) throws Exception {
    String serializedResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(apiFormat, workspaceId.toString()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(request),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return serializedResponse;
  }

  private void assertReferenceResourceCommonFields(
      ApiReferenceResourceCommonFields expectedFields, ApiResourceMetadata resourceFields) {
    assertEquals(expectedFields.getName(), resourceFields.getName());
    assertEquals(expectedFields.getDescription(), resourceFields.getDescription());
    assertEquals(expectedFields.getCloningInstructions(), resourceFields.getCloningInstructions());
    assertEquals(expectedFields.getProperties(), resourceFields.getProperties());
  }
}
