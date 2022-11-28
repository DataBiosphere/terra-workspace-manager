package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.defaultBigQueryDatasetCreationParameters;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi;
import static bio.terra.workspace.common.utils.MockMvcUtils.CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.controller.shared.PropertiesUtils;
import bio.terra.workspace.common.BaseUnitTestMockGcpCloudContextService;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiAiNotebookCloudId;
import bio.terra.workspace.generated.model.ApiBqDatasetCloudId;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiGcsBucketCloudName;
import bio.terra.workspace.generated.model.ApiGenerateGcpAiNotebookCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpBigQueryDatasetCloudIDRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpGcsBucketCloudNameRequestBody;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** ControlledGcpResourceApiController unit tests. */
public class ControlledGcpResourceApiControllerTest extends BaseUnitTestMockGcpCloudContextService {

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  public void setup() throws InterruptedException {
    when(mockGcpCloudContextService().getRequiredGcpProject(any())).thenReturn("fake-project-id");

    // Needed for assertion that requester has role on workspace.
    when(mockSamService().listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));

    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
  }

  @Test
  public void cloneControlledBqDataset_requestContainsInvalidField_throws400() throws Exception {
    // Cannot set destinationDatasetName for COPY_REFERENCE clone
    mockMvcUtils.cloneControlledBqDatasetAsync(
        USER_REQUEST,
        /*sourceWorkspaceId=*/ UUID.randomUUID(),
        /*sourceResourceId=*/ UUID.randomUUID(),
        /*destWorkspaceId=*/ UUID.randomUUID(),
        ApiCloningInstructionsEnum.REFERENCE,
        /*destResourceName=*/ null,
        "datasetName",
        HttpStatus.SC_BAD_REQUEST,
        /*shouldUndo=*/ false);
  }

  @Test
  public void cloneGcsBucket_badRequest_throws400() throws Exception {
    // Cannot set bucketName for COPY_REFERENCE clone
    mockMvcUtils.cloneControlledGcsBucketWithError(
        USER_REQUEST,
        /*sourceWorkspaceId=*/ UUID.randomUUID(),
        /*sourceResourceId=*/ UUID.randomUUID(),
        /*destWorkspaceId=*/ UUID.randomUUID(),
        ApiCloningInstructionsEnum.REFERENCE,
        "bucketName",
        HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void getCloudNameFromGcsBucketName() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiGenerateGcpGcsBucketCloudNameRequestBody bucketNameRequest =
        new ApiGenerateGcpGcsBucketCloudNameRequestBody().gcsBucketName("my-bucket");

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(bucketNameRequest)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiGcsBucketCloudName generatedGcsBucketName =
        objectMapper.readValue(serializedGetResponse, ApiGcsBucketCloudName.class);

    String projectId = mockGcpCloudContextService().getRequiredGcpProject(workspaceId);
    assertEquals(
        generatedGcsBucketName.getGeneratedBucketCloudName(),
        bucketNameRequest.getGcsBucketName() + "-" + projectId);
  }

  @Test
  public void getCloudNameFromBigQueryDatasetName() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiGenerateGcpBigQueryDatasetCloudIDRequestBody bqDatasetNameRequest =
        new ApiGenerateGcpBigQueryDatasetCloudIDRequestBody().bigQueryDatasetName("bq-dataset");

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(bqDatasetNameRequest)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiBqDatasetCloudId generatedBqDatasetName =
        objectMapper.readValue(serializedGetResponse, ApiBqDatasetCloudId.class);
    assertEquals(
        generatedBqDatasetName.getGeneratedDatasetCloudId(),
        bqDatasetNameRequest.getBigQueryDatasetName().replace("-", "_"));
  }

  @Test
  public void getCloudNameFromAiNotebookInstanceName() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiGenerateGcpAiNotebookCloudIdRequestBody aiNotebookNameRequest =
        new ApiGenerateGcpAiNotebookCloudIdRequestBody().aiNotebookName("ai-notebook");

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(aiNotebookNameRequest)),
                    USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiAiNotebookCloudId generatedAiNotebookName =
        objectMapper.readValue(serializedGetResponse, ApiAiNotebookCloudId.class);
    assertEquals(
        generatedAiNotebookName.getGeneratedAiNotebookAiNotebookCloudId(),
        aiNotebookNameRequest.getAiNotebookName());
  }

  @Test
  public void createBigQueryDataset_resourceContainsInvalidFolderId_throws400() throws Exception {
    UUID workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateControlledGcpBigQueryDatasetRequestBody datasetCreationRequest =
        new ApiCreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                makeDefaultControlledResourceFieldsApi()
                    .properties(
                        PropertiesUtils.convertMapToApiProperties(Map.of(FOLDER_ID_KEY, "root"))))
            .dataset(defaultBigQueryDatasetCreationParameters());

    mockMvcUtils.postExpect(
        objectMapper.writeValueAsString(datasetCreationRequest),
        String.format(CONTROLLED_GCP_BIG_QUERY_DATASETS_V1_PATH_FORMAT, workspaceId),
        HttpStatus.SC_BAD_REQUEST);
  }
}
