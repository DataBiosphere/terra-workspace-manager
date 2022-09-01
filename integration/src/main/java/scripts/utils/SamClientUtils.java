package scripts.utils;

import bio.terra.common.sam.SamRetry;
import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import java.util.List;
import javax.annotation.Nullable;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.GoogleApi;
import org.broadinstitute.dsde.workbench.client.sam.api.GroupApi;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyResponseEntryV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for making direct calls to Sam incidental to WSM tests. This cannot be merged with
 * similar methods in ClientTestUtils as they use different ApiClient objects. Direct calls to Sam
 * should usually be wrapped in SamRetry.retry() to ensure transient errors are retried, but
 * legitimated failures aren't run repeatedly.
 */
public class SamClientUtils {

  private static final Logger logger = LoggerFactory.getLogger(SamClientUtils.class);

  private static ApiClient getSamApiClient(
      TestUserSpecification testUser, ServerSpecification server) throws Exception {
    AccessToken accessToken = null;

    // if no test user is specified, then return a client object without an access token set
    // this is useful if the caller wants to make ONLY unauthenticated calls
    if (testUser != null) {
      logger.debug(
          "Fetching credentials and building Sam ApiClient object for test user: {}",
          testUser.name);
      GoogleCredentials userCredential =
          AuthenticationUtils.getDelegatedUserCredential(
              testUser, ClientTestUtils.TEST_USER_SCOPES);
      accessToken = AuthenticationUtils.getAccessToken(userCredential);
    }
    return buildSamClient(accessToken, server);
  }

  private static ApiClient buildSamClient(
      @Nullable AccessToken accessToken, ServerSpecification server) {
    if (Strings.isNullOrEmpty(server.samUri)) {
      throw new IllegalArgumentException("Sam URI cannot be empty");
    }

    // build the client object
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(server.samUri);

    if (accessToken != null) {
      apiClient.setAccessToken(accessToken.getTokenValue());
    }
    return apiClient;
  }

  public static GoogleApi samGoogleApi(TestUserSpecification testUser, ServerSpecification server)
      throws Exception {
    return new GoogleApi(getSamApiClient(testUser, server));
  }

  public static GroupApi samGroupApi(TestUserSpecification testUser, ServerSpecification server)
      throws Exception {
    return new GroupApi(getSamApiClient(testUser, server));
  }

  // dump the Sam policy on a resource - assuming you have permission
  public static void dumpResourcePolicy(
      TestUserSpecification testUser,
      ServerSpecification server,
      String resourceTypeName,
      String resourceId)
      throws Exception {

    ResourcesApi samApi = new ResourcesApi(getSamApiClient(testUser, server));
    List<AccessPolicyResponseEntryV2> policies =
        SamRetry.retry(() -> samApi.listResourcePoliciesV2(resourceTypeName, resourceId));
    logger.info("SAM POLICY DUMP for {} id {}", resourceTypeName, resourceId);
    for (AccessPolicyResponseEntryV2 entry : policies) {
      logger.info("  policy: {}", entry);
    }
  }
}
