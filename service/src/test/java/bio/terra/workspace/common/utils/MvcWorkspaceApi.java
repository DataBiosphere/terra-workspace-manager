package bio.terra.workspace.common.utils;

import bio.terra.workspace.generated.model.ApiDeleteCloudContextV2Request;
import bio.terra.workspace.generated.model.ApiDeleteWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/*
 * NOTE: I am putting the workspaceV2 endpoints here. The current MockMvcUtils file is
 * too big (~3000 loc) and should be busted up. This is a downpayment...
 */
@Component
public class MvcWorkspaceApi {
  private static final Logger logger = LoggerFactory.getLogger(MvcWorkspaceApi.class);

  public static final String WORKSPACES_V2_PATH = "/api/workspaces/v2";
  public static final String WORKSPACES_V2_DELETE = "/api/workspaces/v2/%s/delete";
  public static final String WORKSPACES_V2_DELETE_RESULT = "/api/workspaces/v2/%s/delete-result/%s";
  public static final String CLOUD_CONTEXT_V2_DELETE = "/api/workspaces/v2/%s/cloudcontexts/%s/delete";
  public static final String CLOUD_CONTEXT_V2_DELETE_RESULT = "/api/workspaces/v2/cloudcontexts/%s/delete-result/%s";

  private static final Duration POLLING_INTERVAL = Duration.ofSeconds(15);
  // Do not Autowire UserAccessUtils. UserAccessUtils are for connected tests and not unit tests
  // (since unit tests don't use real SAM). Instead, each method must take in userRequest.
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  public ApiJobResult deleteWorkspaceAsync(AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    logger.info("mvc-deleteWorkspaceAsync user: {} workspaceId: {} jobId: {}", userRequest.getEmail(), workspaceId, jobId);
    var path = String.format(WORKSPACES_V2_DELETE, workspaceId);
    var request = new ApiDeleteWorkspaceV2Request().jobControl(new ApiJobControl().id(jobId));
    var response =
      mockMvc
        .perform(
          MockMvcUtils.addJsonContentType(
            MockMvcUtils.addAuth(
              post(path).content(objectMapper.writeValueAsString(request)), userRequest)))
        .andReturn()
        .getResponse();
    return getCheckedJobResult(response);
  }

  public ApiJobResult deleteWorkspaceAsyncResult(AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    logger.info("mvc-deleteWorkspaceAsyncResult user: {} workspaceId: {} jobId: {}", userRequest.getEmail(), workspaceId, jobId);
    var path = String.format(WORKSPACES_V2_DELETE_RESULT, workspaceId, jobId);
    var response =
      mockMvc
        .perform(MockMvcUtils.addJsonContentType(MockMvcUtils.addAuth(get(path), userRequest)))
        .andReturn()
        .getResponse();
    return getCheckedJobResult(response);
  }

  public ApiJobResult deleteWorkspaceAndWait(AuthenticatedUserRequest userRequest, UUID workspaceId) throws Exception {
    var jobId = UUID.randomUUID().toString();
    var jobResult = deleteWorkspaceAsync(userRequest, workspaceId, jobId);
    while (jobResult.getJobReport().getStatusCode() == HttpStatus.SC_ACCEPTED) {
      TimeUnit.SECONDS.sleep(POLLING_INTERVAL.getSeconds());
      jobResult = deleteWorkspaceAsyncResult(userRequest, workspaceId, jobId);
    }
    return jobResult;
  }

  public ApiJobResult deleteCloudContextAsync(AuthenticatedUserRequest userRequest, UUID workspaceId, CloudPlatform cloudPlatform, String jobId) throws Exception {
    logger.info("mvc-deleteCloudContextAsync user: {} workspaceId: {} cloud: {} jobId: {}", userRequest.getEmail(), workspaceId, cloudPlatform, jobId);
    var path = String.format(CLOUD_CONTEXT_V2_DELETE, workspaceId, cloudPlatform.toApiModel().toString());
    var request = new ApiDeleteCloudContextV2Request().jobControl(new ApiJobControl().id(jobId));
    var response =
      mockMvc
        .perform(
          MockMvcUtils.addJsonContentType(
            MockMvcUtils.addAuth(
              post(path).content(objectMapper.writeValueAsString(request)), userRequest)))
        .andReturn()
        .getResponse();
    return getCheckedJobResult(response);
  }

  public ApiJobResult deleteCloudContextAsyncResult(AuthenticatedUserRequest userRequest, UUID workspaceId, String jobId) throws Exception {
    logger.info("mvc-deleteWorkspaceAsyncResult user: {} workspaceId: {} jobId: {}", userRequest.getEmail(), workspaceId, jobId);
    var path = String.format(CLOUD_CONTEXT_V2_DELETE_RESULT, workspaceId, jobId);
    var response =
      mockMvc
        .perform(MockMvcUtils.addJsonContentType(MockMvcUtils.addAuth(get(path), userRequest)))
        .andReturn()
        .getResponse();
    return getCheckedJobResult(response);
  }

  public ApiJobResult deleteCloudContextAndWait(AuthenticatedUserRequest userRequest, UUID workspaceId, CloudPlatform cloudPlatform) throws Exception {
    var jobId = UUID.randomUUID().toString();
    var jobResult = deleteCloudContextAsync(userRequest, workspaceId, cloudPlatform, jobId);
    while (jobResult.getJobReport().getStatusCode() == HttpStatus.SC_ACCEPTED) {
      TimeUnit.SECONDS.sleep(POLLING_INTERVAL.getSeconds());
      jobResult = deleteCloudContextAsyncResult(userRequest, workspaceId, jobId);
    }
    return jobResult;
  }

  private ApiJobResult getCheckedJobResult(MockHttpServletResponse response) throws Exception {
    int statusCode = response.getStatus();
    String content = response.getContentAsString();
    if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_ACCEPTED) {
      return objectMapper.readValue(content, ApiJobResult.class);
    }
    Assertions.fail(String.format("Expected OK or ACCEPTED, but received %d; body: %s", statusCode, content));
    return null;
  }

}
