package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.FAKE_AUTH_USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseUnitTestMockGcpCloudContextService;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.generated.model.ApiAiNotebookCloudId;
import bio.terra.workspace.generated.model.ApiBqDatasetCloudId;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiGcsBucketCloudName;
import bio.terra.workspace.generated.model.ApiGenerateGcpAiNotebookCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpBigQueryDatasetCloudIDRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpGcsBucketCloudNameRequestBody;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Use this instead of ControlledGcpResourceApiControllerConnectedTest if you don't want to talk to
 * real GCP.
 */
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
  public void cloneGcsBucket_badRequest_throws400() throws Exception {
    // Cannot set bucketName for COPY_REFERENCE clone
    mockMvcUtils.cloneControlledGcsBucketAsync(
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
    UUID workspaceId =
        mockMvcUtils.createWorkspaceWithoutCloudContext(FAKE_AUTH_USER_REQUEST).getId();
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
                    FAKE_AUTH_USER_REQUEST))
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
    UUID workspaceId =
        mockMvcUtils.createWorkspaceWithoutCloudContext(FAKE_AUTH_USER_REQUEST).getId();
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
                    FAKE_AUTH_USER_REQUEST))
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
    UUID workspaceId =
        mockMvcUtils.createWorkspaceWithoutCloudContext(FAKE_AUTH_USER_REQUEST).getId();
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
                    FAKE_AUTH_USER_REQUEST))
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
