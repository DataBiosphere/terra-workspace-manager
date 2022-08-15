package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.CREATE_CLOUD_CONTEXT_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_AI_NOTEBOOK_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_BQ_DATASET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GENERATE_GCP_GCS_BUCKET_NAME_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.GET_CLOUD_CONTEXT_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiCloudAiNotebookName;
import bio.terra.workspace.generated.model.ApiCloudBqDatasetName;
import bio.terra.workspace.generated.model.ApiCloudBucketName;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookGenerateCloudNameRequestBody;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetGenerateCloudNameRequestBody;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketGenerateCloudNameRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiTpsPolicyInput;
import bio.terra.workspace.generated.model.ApiTpsPolicyPair;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
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
  /** A fake group-constraint policy for a workspace. */
  private static final ApiTpsPolicyInput GROUP_POLICY =
      new ApiTpsPolicyInput()
          .namespace("terra")
          .name("group-constraint")
          .addAdditionalDataItem(new ApiTpsPolicyPair().key("group").value("my_fake_group"));

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired GcpCloudContextService gcpCloudContextService;
  @MockBean FeatureConfiguration mockFeatureConfiguration;
  @MockBean TpsApiDispatch mockTpsApiDispatch;
  @MockBean SamService mockSamService;

  @BeforeEach
  public void setup() throws InterruptedException {
    when(mockSamService.isAuthorized(
            any(), eq(SamResource.SPEND_PROFILE), any(), eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    when(mockSamService.listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
    when(mockSamService.getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));

    when(mockFeatureConfiguration.isTpsEnabled()).thenReturn(true);
    // We don't need to mock tpsCheck() because Mockito will already do nothing by default.
  }

  @Test
  public void getCloudNameFromGcsBucketName() throws Exception {
    UUID workspaceId = createDefaultWorkspace().getId();
    createWorkspaceAndCloudContext(workspaceId);

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
    ApiGcpGcsBucketGenerateCloudNameRequestBody bucketNameRequest =
        new ApiGcpGcsBucketGenerateCloudNameRequestBody().bucketName("my-bucket");

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

    ApiCloudBucketName generatedGcsBucketName =
        objectMapper.readValue(serializedGetResponse, ApiCloudBucketName.class);
    assertEquals(
        bucketNameRequest.getBucketName() + "-" + projectId,
        generatedGcsBucketName.getGeneratedCloudBucketName());
  }

  @Test
  public void getCloudNameFromBigQueryDatasetName() throws Exception {
    UUID workspaceId = createDefaultWorkspace().getId();
    createWorkspaceAndCloudContext(workspaceId);

    ApiGcpBigQueryDatasetGenerateCloudNameRequestBody bqDatasetNameRequest =
        new ApiGcpBigQueryDatasetGenerateCloudNameRequestBody().bigQueryDatasetName("bq-dataset");

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

    ApiCloudBqDatasetName generatedBqDatasetName =
        objectMapper.readValue(serializedGetResponse, ApiCloudBqDatasetName.class);
    assertEquals(
        bqDatasetNameRequest.getBigQueryDatasetName().replace("-", "_"),
        generatedBqDatasetName.getGeneratedCloudDatasetName());
  }

  @Test
  public void getCloudNameFromAiNotebookInstanceName() throws Exception {
    UUID workspaceId = createDefaultWorkspace().getId();
    createWorkspaceAndCloudContext(workspaceId);

    ApiGcpAiNotebookGenerateCloudNameRequestBody aiNotebookNameRequest =
        new ApiGcpAiNotebookGenerateCloudNameRequestBody().aiNotebookName("ai-notebook");

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

    ApiCloudAiNotebookName generatedAiNotebookName =
        objectMapper.readValue(serializedGetResponse, ApiCloudAiNotebookName.class);
    assertEquals(
        aiNotebookNameRequest.getAiNotebookName(),
        generatedAiNotebookName.getGeneratedCloudAiNotebookName());
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

  private void createWorkspaceAndCloudContext(UUID workspaceId) throws Exception {

    String fakeJobId = workspaceId.toString();

    ApiCreateCloudContextRequest request =
        new ApiCreateCloudContextRequest()
            .cloudPlatform(ApiCloudPlatform.GCP)
            .jobControl(new ApiJobControl().id(fakeJobId));
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(CREATE_CLOUD_CONTEXT_PATH_FORMAT, workspaceId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)),
                    USER_REQUEST)))
        .andExpect(status().is(HttpStatus.SC_ACCEPTED));

    for (int i = 0; i < 20; i++) {
      try {
        mockMvc
            .perform(
                (addAuth(
                    get(String.format(GET_CLOUD_CONTEXT_PATH_FORMAT, workspaceId, fakeJobId)),
                    USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_OK));
      } catch (AssertionError e) {
        System.out.println("Try it again.");
        Thread.sleep(1000);
        continue;
      }
      break;
    }
  }
}
