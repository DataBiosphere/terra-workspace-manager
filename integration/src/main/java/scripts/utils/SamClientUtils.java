package scripts.utils;

import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import java.io.IOException;
import javax.annotation.Nullable;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.GoogleApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for making direct calls to Sam incidental to WSM tests. This cannot be merged with
 * similar methods in ClientTestUtils as they use different ApiClient objects.
 */
public class SamClientUtils {

  private static final Logger logger = LoggerFactory.getLogger(SamClientUtils.class);

  private static ApiClient getSamApiClient(TestUserSpecification testUser, String samUri) throws Exception {
    AccessToken accessToken = null;

    // if no test user is specified, then return a client object without an access token set
    // this is useful if the caller wants to make ONLY unauthenticated calls
    if (testUser != null) {
      logger.debug(
          "Fetching credentials and building Sam ApiClient object for test user: {}",
          testUser.name);
      // TODO(PF-657): TestRunner caches delegated credentials by TestUser, ignoring scopes. This
      //  should change, but for now I include all scopes we'll need on this user in the first call.
      GoogleCredentials userCredential =
          AuthenticationUtils
              .getDelegatedUserCredential(testUser, ClientTestUtils.TEST_USER_SCOPES);
      accessToken = AuthenticationUtils.getAccessToken(userCredential);
    }
    return buildSamClient(accessToken, samUri);
  }

  private static ApiClient buildSamClient(
      @Nullable AccessToken accessToken, String samUri) throws IOException {
    if (Strings.isNullOrEmpty(samUri)) {
      throw new IllegalArgumentException("Sam URI cannot be empty");
    }

    // build the client object
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(samUri);

    if (accessToken != null) {
      apiClient.setAccessToken(accessToken.getTokenValue());
    }
    return apiClient;
  }

  public static GoogleApi samGoogleApi(TestUserSpecification testUser, String samUri) throws Exception {
    return new GoogleApi(getSamApiClient(testUser, samUri));
  }

}
