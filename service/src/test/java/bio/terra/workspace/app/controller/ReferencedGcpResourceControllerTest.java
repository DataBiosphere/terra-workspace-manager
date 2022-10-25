package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeBqDataTableReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeDataRepoSnapshotReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeDefaultReferencedResourceFieldsApi;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeGcpBqDatasetReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeGcsBucketReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeGcsObjectReferenceRequestBody;
import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.makeGitRepoReferenceRequestBody;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GCP_GCS_OBJECTS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.REFERENCED_GIT_REPO_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.controller.shared.PropertiesUtils;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.utils.MockMvcUtils;
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
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class ReferencedGcpResourceControllerTest extends BaseUnitTest {

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  public void setUp() throws InterruptedException {
    // Needed for workspace creation as logging is triggered when a workspace is created in
    // `WorkspaceActivityLogHook` where we extract the user request information and log it to
    // activity log.
    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
    // Needed for assertion that requester has role on workspace.
    when(mockSamService().listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
  }

  @Test
  public void createReferencedDataRepoResource_commonFieldsAndAttributesCorrectlyPopulated()
      throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateDataRepoSnapshotReferenceRequestBody requestBody =
        makeDataRepoSnapshotReferenceRequestBody();

    ApiDataRepoSnapshotResource createdResource =
        createReferencedDataRepoSnapshotResource(workspaceId, requestBody);

    assertReferenceResourceCommonFields(requestBody.getMetadata(), createdResource.getMetadata());
    assertEquals(
        requestBody.getSnapshot().getSnapshot(), createdResource.getAttributes().getSnapshot());
    assertEquals(
        requestBody.getSnapshot().getInstanceName(),
        createdResource.getAttributes().getInstanceName());
  }

  @Test
  public void createReferencedDataRepoResource_resourceContainsInvalidFolderId_throws400()
      throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateDataRepoSnapshotReferenceRequestBody requestBody =
        makeDataRepoSnapshotReferenceRequestBody()
            .metadata(
                makeDefaultReferencedResourceFieldsApi()
                    .properties(
                        PropertiesUtils.convertMapToApiProperties(Map.of(FOLDER_ID_KEY, "root"))));

    mockMvcUtils.postExpect(
        objectMapper.writeValueAsString(requestBody),
        String.format(REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT, workspaceId),
        HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void createReferencedGcsBucketResource_commonFieldsAndAttributesCorrectlyPopulated()
      throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateGcpGcsBucketReferenceRequestBody requestBody = makeGcsBucketReferenceRequestBody();

    ApiGcpGcsBucketResource createdResource =
        createReferencedGcsBucketResource(workspaceId, requestBody);

    assertReferenceResourceCommonFields(requestBody.getMetadata(), createdResource.getMetadata());
    assertEquals(
        requestBody.getBucket().getBucketName(), createdResource.getAttributes().getBucketName());
  }

  @Test
  public void createReferencedGcsObjectResource_commonFieldsAndAttributesCorrectlyPopulated()
      throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateGcpGcsObjectReferenceRequestBody requestBody = makeGcsObjectReferenceRequestBody();

    ApiGcpGcsObjectResource createdResource =
        createReferencedGcsObjectResource(workspaceId, requestBody);

    assertReferenceResourceCommonFields(requestBody.getMetadata(), createdResource.getMetadata());
    assertEquals(
        requestBody.getFile().getBucketName(), createdResource.getAttributes().getBucketName());
    assertEquals(
        requestBody.getFile().getFileName(), createdResource.getAttributes().getFileName());
  }

  @Test
  public void createReferencedBqDatasetResource_commonFieldsAndAttributesCorrectlyPopulated()
      throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateGcpBigQueryDatasetReferenceRequestBody requestBody =
        makeGcpBqDatasetReferenceRequestBody();

    ApiGcpBigQueryDatasetResource createdResource =
        createReferencedBigQueryDatasetResource(workspaceId, requestBody);

    assertReferenceResourceCommonFields(requestBody.getMetadata(), createdResource.getMetadata());
    assertEquals(
        requestBody.getDataset().getDatasetId(), createdResource.getAttributes().getDatasetId());
    assertEquals(
        requestBody.getDataset().getProjectId(), createdResource.getAttributes().getProjectId());
  }

  @Test
  public void createReferencedBqDatatableResource_commonFieldsAndAttributesCorrectlyPopulated()
      throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateGcpBigQueryDataTableReferenceRequestBody requestBody =
        makeBqDataTableReferenceRequestBody();

    ApiGcpBigQueryDataTableResource createdResource =
        createReferencedBigQueryDataTableResource(workspaceId, requestBody);

    assertReferenceResourceCommonFields(requestBody.getMetadata(), createdResource.getMetadata());
    assertEquals(
        requestBody.getDataTable().getDataTableId(),
        createdResource.getAttributes().getDataTableId());
    assertEquals(
        requestBody.getDataTable().getDatasetId(), createdResource.getAttributes().getDatasetId());
    assertEquals(
        requestBody.getDataTable().getProjectId(), createdResource.getAttributes().getProjectId());
  }

  @Test
  public void createReferencedGitRepoResource_commonFieldsAndAttributesCorrectlyPopulated()
      throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateGitRepoReferenceRequestBody requestBody = makeGitRepoReferenceRequestBody();

    ApiGitRepoResource createdResource = createReferencedGitRepoResource(workspaceId, requestBody);

    assertReferenceResourceCommonFields(requestBody.getMetadata(), createdResource.getMetadata());
    assertEquals(
        requestBody.getGitrepo().getGitRepoUrl(), createdResource.getAttributes().getGitRepoUrl());
  }

  public ApiDataRepoSnapshotResource createReferencedDataRepoSnapshotResource(
      UUID workspaceId, ApiCreateDataRepoSnapshotReferenceRequestBody requestBody)
      throws Exception {
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_DATA_REPO_SNAPSHOTS_V1_PATH_FORMAT);

    return objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
  }

  private ApiGcpGcsBucketResource createReferencedGcsBucketResource(
      UUID workspaceId, ApiCreateGcpGcsBucketReferenceRequestBody requestBody) throws Exception {
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GCP_GCS_BUCKETS_V1_PATH_FORMAT);

    return objectMapper.readValue(serializedResponse, ApiGcpGcsBucketResource.class);
  }

  private ApiGcpGcsObjectResource createReferencedGcsObjectResource(
      UUID workspaceId, ApiCreateGcpGcsObjectReferenceRequestBody requestBody) throws Exception {
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GCP_GCS_OBJECTS_V1_PATH_FORMAT);

    return objectMapper.readValue(serializedResponse, ApiGcpGcsObjectResource.class);
  }

  private ApiGcpBigQueryDatasetResource createReferencedBigQueryDatasetResource(
      UUID workspaceId, ApiCreateGcpBigQueryDatasetReferenceRequestBody requestBody)
      throws Exception {
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT);

    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDatasetResource.class);
  }

  private ApiGcpBigQueryDataTableResource createReferencedBigQueryDataTableResource(
      UUID workspaceId, ApiCreateGcpBigQueryDataTableReferenceRequestBody requestBody)
      throws Exception {
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GCP_BIG_QUERY_DATA_TABLE_V1_PATH_FORMAT);

    return objectMapper.readValue(serializedResponse, ApiGcpBigQueryDataTableResource.class);
  }

  private ApiGitRepoResource createReferencedGitRepoResource(
      UUID workspaceId, ApiCreateGitRepoReferenceRequestBody requestBody) throws Exception {
    var request = objectMapper.writeValueAsString(requestBody);

    String serializedResponse =
        createReferencedResourceAndGetSerializedResponse(
            workspaceId, request, REFERENCED_GIT_REPO_V1_PATH_FORMAT);

    return objectMapper.readValue(serializedResponse, ApiGitRepoResource.class);
  }

  private String createReferencedResourceAndGetSerializedResponse(
      UUID workspaceId, String request, String apiFormat) throws Exception {
    return mockMvc
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
  }

  private void assertReferenceResourceCommonFields(
      ApiReferenceResourceCommonFields expectedFields, ApiResourceMetadata resourceFields) {
    assertEquals(expectedFields.getName(), resourceFields.getName());
    assertEquals(expectedFields.getDescription(), resourceFields.getDescription());
    assertEquals(expectedFields.getCloningInstructions(), resourceFields.getCloningInstructions());
    assertEquals(expectedFields.getProperties(), resourceFields.getProperties());
  }
}
