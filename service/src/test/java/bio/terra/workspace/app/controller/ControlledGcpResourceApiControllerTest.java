package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.MockBeanUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiAiNotebookCloudId;
import bio.terra.workspace.generated.model.ApiBqDatasetCloudId;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiGcsBucketCloudName;
import bio.terra.workspace.generated.model.ApiGenerateGcpAiNotebookCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpBigQueryDatasetCloudIDRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpGcsBucketCloudNameRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Disabled("PF-1962 This test needs to mock Sam to be a proper unit test")
public class ControlledGcpResourceApiControllerTest extends MockBeanUnitTest {

  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  // This extra mock is shared in two tests, but not shared in other unit tests
  // so we will end up with another app context. Shared with ControlledGcsBucketHandlerTest.
  @MockBean GcpCloudContextService mockGcpCloudContextService;

  private GcpCloudContextService getMockGcpCloudContextService() {
    return mockGcpCloudContextService;
  }

  @BeforeEach
  public void setup() throws InterruptedException {
    when(getMockGcpCloudContextService().getRequiredGcpProject(any()))
        .thenReturn("fake-project-id");
  }

  @Test
  public void getCloudNameFromGcsBucketName() throws Exception {
    UUID workspaceId = createDefaultWorkspace().getId();
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

    String projectId = getMockGcpCloudContextService().getRequiredGcpProject(workspaceId);
    assertEquals(
        generatedGcsBucketName.getGeneratedBucketCloudName(),
        bucketNameRequest.getGcsBucketName() + "-" + projectId);
  }

  @Test
  public void getCloudNameFromBigQueryDatasetName() throws Exception {
    UUID workspaceId = createDefaultWorkspace().getId();
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
    UUID workspaceId = createDefaultWorkspace().getId();
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

  private ApiCreatedWorkspace createDefaultWorkspace() throws Exception {
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
}
