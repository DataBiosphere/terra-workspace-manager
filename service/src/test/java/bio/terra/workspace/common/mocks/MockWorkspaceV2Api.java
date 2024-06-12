package bio.terra.workspace.common.mocks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.generated.model.ApiDeleteCloudContextV2Request;
import bio.terra.workspace.generated.model.ApiDeleteWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

@Component
public class MockWorkspaceV2Api {
  private static final Logger logger = LoggerFactory.getLogger(MockWorkspaceV2Api.class);

  private static final Duration POLLING_INTERVAL = Duration.ofSeconds(15);

  // Do not Autowire UserAccessUtils. UserAccessUtils are for connected tests and not unit tests
  // (since unit tests don't use real SAM). Instead, each method must take in userRequest.
  @Autowired private MockMvc mockMvc;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private ObjectMapper objectMapper;

  // Workspace

  public static final String WORKSPACES_V2_CREATE = "/api/workspaces/v2";
  public static final String WORKSPACES_V2_CREATE_RESULT = WORKSPACES_V2_CREATE + "/result/%s";
  public static final String WORKSPACES_V2 = WORKSPACES_V2_CREATE + "/%s";
  public static final String WORKSPACES_V2_DELETE = WORKSPACES_V2 + "/delete";
  public static final String WORKSPACES_V2_DELETE_RESULT = WORKSPACES_V2 + "/delete-result/%s";

  // pass null cloudPlatform to skip creating a cloud context
  public ApiCreateWorkspaceV2Result createWorkspaceAsync(
      AuthenticatedUserRequest userRequest, @Nullable ApiCloudPlatform cloudPlatform, String jobId)
      throws Exception {
    ApiCreateWorkspaceV2Request request =
        new ApiCreateWorkspaceV2Request()
            .id(UUID.randomUUID())
            .cloudPlatform(cloudPlatform)
            .spendProfile(
                cloudPlatform == ApiCloudPlatform.AZURE
                    ? WorkspaceFixtures.DEFAULT_AZURE_SPEND_PROFILE_NAME
                    : WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_NAME)
            .stage(ApiWorkspaceStageModel.MC_WORKSPACE)
            .jobControl(new ApiJobControl().id(jobId));

    logger.info(
        "mvc-createWorkspaceAsync user: {} workspaceId: {} cloudPlatform: {} jobId: {}",
        userRequest.getEmail(),
        request.getId(),
        request.getCloudPlatform(),
        jobId);

    MockHttpServletResponse response =
        mockMvc
            .perform(
                MockMvcUtils.addJsonContentType(
                    MockMvcUtils.addAuth(
                        post(WORKSPACES_V2_CREATE)
                            .content(objectMapper.writeValueAsString(request)),
                        userRequest)))
            .andReturn()
            .getResponse();

    return mockMvcUtils.getCheckedJobResult(response, ApiCreateWorkspaceV2Result.class);
  }

  public ApiCreateWorkspaceV2Result createWorkspaceAsyncResult(
      AuthenticatedUserRequest userRequest, String jobId) throws Exception {
    logger.info("mvc-createWorkspaceAsyncResult user: {} jobId: {}", userRequest.getEmail(), jobId);
    String path = String.format(WORKSPACES_V2_CREATE_RESULT, jobId);
    MockHttpServletResponse response =
        mockMvc
            .perform(MockMvcUtils.addJsonContentType(MockMvcUtils.addAuth(get(path), userRequest)))
            .andReturn()
            .getResponse();
    return mockMvcUtils.getCheckedJobResult(response, ApiCreateWorkspaceV2Result.class);
  }

  public ApiCreateWorkspaceV2Result createWorkspaceAndWait(
      AuthenticatedUserRequest userRequest, ApiCloudPlatform cloudPlatform) throws Exception {
    String jobId = UUID.randomUUID().toString();
    ApiCreateWorkspaceV2Result jobResult = createWorkspaceAsync(userRequest, cloudPlatform, jobId);
    while (jobResult.getJobReport().getStatusCode() == HttpStatus.SC_ACCEPTED) {
      TimeUnit.SECONDS.sleep(POLLING_INTERVAL.getSeconds());
      jobResult = createWorkspaceAsyncResult(userRequest, jobId);
    }
    return jobResult;
  }

  public ApiJobResult deleteWorkspaceAsync(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    logger.info(
        "mvc-deleteWorkspaceAsync user: {} workspaceId: {} jobId: {}",
        userRequest.getEmail(),
        workspaceId,
        jobId);
    String path = String.format(WORKSPACES_V2_DELETE, workspaceId);
    ApiDeleteWorkspaceV2Request request =
        new ApiDeleteWorkspaceV2Request().jobControl(new ApiJobControl().id(jobId));
    MockHttpServletResponse response =
        mockMvc
            .perform(
                MockMvcUtils.addJsonContentType(
                    MockMvcUtils.addAuth(
                        post(path).content(objectMapper.writeValueAsString(request)), userRequest)))
            .andReturn()
            .getResponse();
    return mockMvcUtils.getCheckedJobResult(response, ApiJobResult.class);
  }

  public ApiJobResult deleteWorkspaceAsyncResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    logger.info(
        "mvc-deleteWorkspaceAsyncResult user: {} workspaceId: {} jobId: {}",
        userRequest.getEmail(),
        workspaceId,
        jobId);
    String path = String.format(WORKSPACES_V2_DELETE_RESULT, workspaceId, jobId);
    MockHttpServletResponse response =
        mockMvc
            .perform(MockMvcUtils.addJsonContentType(MockMvcUtils.addAuth(get(path), userRequest)))
            .andReturn()
            .getResponse();
    return mockMvcUtils.getCheckedJobResult(response, ApiJobResult.class);
  }

  public ApiJobResult deleteWorkspaceAndWait(AuthenticatedUserRequest userRequest, UUID workspaceId)
      throws Exception {
    String jobId = UUID.randomUUID().toString();
    ApiJobResult jobResult = deleteWorkspaceAsync(userRequest, workspaceId, jobId);
    while (jobResult.getJobReport().getStatusCode() == HttpStatus.SC_ACCEPTED) {
      TimeUnit.SECONDS.sleep(POLLING_INTERVAL.getSeconds());
      jobResult = deleteWorkspaceAsyncResult(userRequest, workspaceId, jobId);
    }
    return jobResult;
  }

  // Cloud context

  public static final String CLOUD_CONTEXTS_V2_CREATE = WORKSPACES_V2 + "/cloudcontexts";
  public static final String CLOUD_CONTEXTS_V2_DELETE = CLOUD_CONTEXTS_V2_CREATE + "/%s/delete";
  public static final String CLOUD_CONTEXTS_V2_DELETE_RESULT =
      CLOUD_CONTEXTS_V2_CREATE + "/delete-result/%s";

  public ApiJobResult deleteCloudContextAsync(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      CloudPlatform cloudPlatform,
      String jobId)
      throws Exception {
    logger.info(
        "mvc-deleteCloudContextAsync user: {} workspaceId: {} cloud: {} jobId: {}",
        userRequest.getEmail(),
        workspaceId,
        cloudPlatform,
        jobId);
    String path =
        String.format(CLOUD_CONTEXTS_V2_DELETE, workspaceId, cloudPlatform.toApiModel().toString());
    ApiDeleteCloudContextV2Request request =
        new ApiDeleteCloudContextV2Request().jobControl(new ApiJobControl().id(jobId));
    MockHttpServletResponse response =
        mockMvc
            .perform(
                MockMvcUtils.addJsonContentType(
                    MockMvcUtils.addAuth(
                        post(path).content(objectMapper.writeValueAsString(request)), userRequest)))
            .andReturn()
            .getResponse();
    return mockMvcUtils.getCheckedJobResult(response, ApiJobResult.class);
  }

  public ApiJobResult deleteCloudContextAsyncResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    logger.info(
        "mvc-deleteWorkspaceAsyncResult user: {} workspaceId: {} jobId: {}",
        userRequest.getEmail(),
        workspaceId,
        jobId);
    String path = String.format(CLOUD_CONTEXTS_V2_DELETE_RESULT, workspaceId, jobId);
    MockHttpServletResponse response =
        mockMvc
            .perform(MockMvcUtils.addJsonContentType(MockMvcUtils.addAuth(get(path), userRequest)))
            .andReturn()
            .getResponse();
    return mockMvcUtils.getCheckedJobResult(response, ApiJobResult.class);
  }

  public ApiJobResult deleteCloudContextAndWait(
      AuthenticatedUserRequest userRequest, UUID workspaceId, CloudPlatform cloudPlatform)
      throws Exception {
    String jobId = UUID.randomUUID().toString();
    ApiJobResult jobResult =
        deleteCloudContextAsync(userRequest, workspaceId, cloudPlatform, jobId);
    while (jobResult.getJobReport().getStatusCode() == HttpStatus.SC_ACCEPTED) {
      TimeUnit.SECONDS.sleep(POLLING_INTERVAL.getSeconds());
      jobResult = deleteCloudContextAsyncResult(userRequest, workspaceId, jobId);
    }
    return jobResult;
  }
}
