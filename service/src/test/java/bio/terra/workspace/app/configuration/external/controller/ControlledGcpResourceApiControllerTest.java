package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.createDefaultWorkspace;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiAiNotebookCloudId;
import bio.terra.workspace.generated.model.ApiBqDatasetCloudId;
import bio.terra.workspace.generated.model.ApiGcsBucketCloudName;
import bio.terra.workspace.generated.model.ApiGenerateGcpAiNotebookCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpBigQueryDatasetCloudIDRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpGcsBucketCloudNameRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public class ControlledGcpResourceApiControllerTest extends BaseUnitTest {

  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean GcpCloudContextService mockGcpCloudContextService;
  @MockBean SamService mockSamService;

  @BeforeEach
  public void setup() throws InterruptedException {
    when(mockGcpCloudContextService.getRequiredGcpProject(any())).thenReturn("fake-project-id");
  }

  @Test
  public void getCloudNameFromGcsBucketName() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
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

    String projectId = mockGcpCloudContextService.getRequiredGcpProject(workspaceId);
    assertEquals(
        generatedGcsBucketName.getGeneratedBucketCloudName(),
        bucketNameRequest.getGcsBucketName() + "-" + projectId);
  }

  @Test
  public void getCloudNameFromBigQueryDatasetName() throws Exception {
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
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
    UUID workspaceId = createDefaultWorkspace(mockMvc, objectMapper).getId();
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
}
