package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.fixtures.ReferenceResourceFixtures.*;
import static bio.terra.workspace.common.utils.MockMvcUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  @Autowired MockMvcUtils mockMvcUtils;
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
    // Needed for assertion that requester has role on workspace.
    when(mockSamService.listRequesterRoles(any(), any(), any()))
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
  public void cloneReferencedDataRepoResource()
          throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateDataRepoSnapshotReferenceRequestBody requestBody =
            makeDataRepoSnapshotReferenceRequestBody();

    ApiDataRepoSnapshotResource createdResource =
            createReferencedDataRepoSnapshotResource(workspaceId, requestBody);

    //Create a second workspace
    UUID workspace2Id = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    var request = makeDataRepoSnapshotCloneReferenceRequestBody(workspace2Id);

    String serializedResponse =
            cloneReferencedResourceAndGetSerializedResponse(
                    workspaceId, createdResource.getMetadata().getResourceId(), objectMapper.writeValueAsString(request), REFERENCED_DATA_REPO_SNAPSHOTS_V1_CLONE_PATH_FORMAT);

    ApiCloneReferencedGcpDataRepoSnapshotResourceResult result = objectMapper.readValue(serializedResponse, ApiCloneReferencedGcpDataRepoSnapshotResourceResult.class);


    assertEquals(
            createdResource.getMetadata().getResourceId(), result.getSourceResourceId());
  }

  @Test
  public void cloneBatchReferencedDataRepoResource()
          throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    List<UUID> resourceIds = new ArrayList<>();
    List<ApiDataRepoSnapshotResource> snapshots = new ArrayList<>();
    for (int i = 0 ; i < 5; i++){
      ApiCreateDataRepoSnapshotReferenceRequestBody request = makeDataRepoSnapshotReferenceRequestBody();
      snapshots.add(createReferencedDataRepoSnapshotResource(workspaceId, request));
      resourceIds.add(snapshots.get(i).getMetadata().getResourceId());
    }

    //Create a second workspace
    UUID workspace2Id = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();

    var request = makeDataRepoSnapshotCloneReferenceRequestBody(workspace2Id, resourceIds);

    String serializedResponse =
            createReferencedResourceAndGetSerializedResponse(
                    workspaceId, objectMapper.writeValueAsString(request), REFERENCED_DATA_REPO_SNAPSHOTS_V1_BATCH_CLONE_PATH_FORMAT);

    ApiCloneReferencedGcpDataRepoSnapshotResourceResultList result = objectMapper.readValue(serializedResponse, ApiCloneReferencedGcpDataRepoSnapshotResourceResultList.class);

  for (int i = 0; i < 5; i++){
    assertEquals(
            snapshots.get(i).getMetadata().getResourceId(), result.get(i).getSourceResourceId());

    }
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
            workspaceId, request, REFERENCED_GCP_BIG_QUERY_DATASET_V1_PATH_FORMAT);

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

  private String cloneReferencedResourceAndGetSerializedResponse(
          UUID workspaceId, UUID resourceId, String request, String apiFormat) throws Exception {
    return mockMvc
            .perform(
                    addAuth(
                            post(String.format(apiFormat, workspaceId.toString(), resourceId.toString()))
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
