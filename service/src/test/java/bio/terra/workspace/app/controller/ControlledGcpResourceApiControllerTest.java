package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.mocks.MockGcpApi.CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.GENERATE_NAME_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.GENERATE_NAME_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockGcpApi.GENERATE_NAME_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.mocks.MockMvcUtils.addAuth;
import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.app.controller.shared.PropertiesUtils;
import bio.terra.workspace.common.BaseSpringBootUnitTestMockGcpCloudContextService;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.generated.model.ApiAiNotebookCloudId;
import bio.terra.workspace.generated.model.ApiBqDatasetCloudId;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiGcsBucketCloudName;
import bio.terra.workspace.generated.model.ApiGenerateGcpAiNotebookCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpBigQueryDatasetCloudIDRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpGcsBucketCloudNameRequestBody;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** ControlledGcpResourceApiController unit tests. */
@Tag("unit")
public class ControlledGcpResourceApiControllerTest
    extends BaseSpringBootUnitTestMockGcpCloudContextService {
  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  public void setup() throws InterruptedException {
    when(mockGcpCloudContextService().getRequiredGcpProject(any())).thenReturn("fake-project-id");

    // Needed for assertion that requester has role on workspace.
    when(mockSamService().listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));

    when(mockSamService()
            .isAuthorized(
                any(),
                eq(SamConstants.SamResource.SPEND_PROFILE),
                any(),
                eq(SamConstants.SamSpendProfileAction.LINK)))
        .thenReturn(true);

    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(any()))
        .thenReturn(USER_REQUEST.getEmail());
  }

  @Test
  public void getCloudNameFromGcsBucketName() throws Exception {
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiGenerateGcpGcsBucketCloudNameRequestBody bucketNameRequest =
        new ApiGenerateGcpGcsBucketCloudNameRequestBody().gcsBucketName("my-bucket");

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(
                            GENERATE_NAME_CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT, workspaceId))
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
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiGenerateGcpBigQueryDatasetCloudIDRequestBody bqDatasetNameRequest =
        new ApiGenerateGcpBigQueryDatasetCloudIDRequestBody().bigQueryDatasetName("bq-dataset");

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(
                            GENERATE_NAME_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT, workspaceId))
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
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiGenerateGcpAiNotebookCloudIdRequestBody aiNotebookNameRequest =
        new ApiGenerateGcpAiNotebookCloudIdRequestBody().aiNotebookName("ai-notebook");

    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    post(String.format(
                            GENERATE_NAME_CONTROLLED_GCP_AI_NOTEBOOKS_PATH_FORMAT, workspaceId))
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
    UUID workspaceId = mockWorkspaceV1Api.createWorkspaceWithoutCloudContext(USER_REQUEST).getId();
    ApiCreateControlledGcpBigQueryDatasetRequestBody datasetCreationRequest =
        new ApiCreateControlledGcpBigQueryDatasetRequestBody()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsApi()
                    .properties(
                        PropertiesUtils.convertMapToApiProperties(Map.of(FOLDER_ID_KEY, "root"))))
            .dataset(ControlledGcpResourceFixtures.defaultBigQueryDatasetCreationParameters());

    mockMvcUtils.postExpect(
        USER_REQUEST,
        objectMapper.writeValueAsString(datasetCreationRequest),
        String.format(CREATE_CONTROLLED_GCP_BQ_DATASETS_PATH_FORMAT, workspaceId),
        HttpStatus.SC_BAD_REQUEST);
  }
}
