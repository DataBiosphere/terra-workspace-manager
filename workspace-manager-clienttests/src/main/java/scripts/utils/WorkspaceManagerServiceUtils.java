package scripts.utils;

import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.client.ApiClient;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WorkspaceManagerServiceUtils {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceManagerServiceUtils.class);

    private static Map<TestUserSpecification, ApiClient> apiClientsForTestUsers = new HashMap<>();

    private WorkspaceManagerServiceUtils() {}

    /**
     * Build the Workspace Manager Service API client object for the given server specification.
     *
     * @param server the server we are testing against
     * @return the API client object
     */
    public static ApiClient getClient(ServerSpecification server) throws IOException {
        if (Strings.isNullOrEmpty(server.workspaceManagerUri)) {
            throw new IllegalArgumentException("Workspace Manager Service URI cannot be empty");
        }
        if (server.testRunnerServiceAccount == null) {
            throw new IllegalArgumentException("Workspace Manager Service client service account is required");
        }

        // refresh the client service account token
        GoogleCredentials serviceAccountCredential =
                AuthenticationUtils.getServiceAccountCredential(
                        server.testRunnerServiceAccount, AuthenticationUtils.userLoginScopes);
        AccessToken accessToken = AuthenticationUtils.getAccessToken(serviceAccountCredential);
        logger.debug(
                "Generated access token for workspace manager service client SA: {}",
                server.testRunnerServiceAccount.name);

        // build the client object
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(server.workspaceManagerUri);
        apiClient.setAccessToken(accessToken.getTokenValue());

        return apiClient;
    }

    /**
     * Build the Workspace Manager API client object for the given test user and server specifications. This
     * class maintains a cache of API client objects, and will return the cached object if it already
     * exists. The token is always refreshed, regardless of whether the API client object was found in
     * the cache or not.
     *
     * @param testUser the test user whose credentials are supplied to the API client object
     * @param server the server we are testing against
     * @return the API client object for this user
     */
    public static ApiClient getClientForTestUser(
            TestUserSpecification testUser, ServerSpecification server) throws IOException {
        if (server.workspaceManagerUri == null || server.workspaceManagerUri.isEmpty()) {
            throw new IllegalArgumentException("Workspace Manager URI cannot be empty");
        }

        // if no test user is specified, then return a client object without an access token set
        // this is useful if the caller wants to make ONLY unauthenticated calls
        if (testUser == null) {
            ApiClient apiClient = new ApiClient();
            apiClient.setBasePath(server.workspaceManagerUri);
        }

        // refresh the user token
        GoogleCredentials userCredential =
                AuthenticationUtils.getDelegatedUserCredential(
                        testUser, AuthenticationUtils.userLoginScopes);
        AccessToken userAccessToken = AuthenticationUtils.getAccessToken(userCredential);
        // first check if there is already a cached ApiClient for this test user
        ApiClient cachedApiClient = apiClientsForTestUsers.get(testUser);
        if (cachedApiClient != null) {
            // refresh the token here before returning
            // this should be helpful for long-running tests (roughly > 1hr)
            cachedApiClient.setAccessToken(userAccessToken.getTokenValue());

            return cachedApiClient;
        }

        // TODO: have ApiClients share an HTTP client, or one per each is ok?
        // no cached ApiClient found, so build a new one here and add it to the cache before returning
        logger.debug(
                "Fetching credentials and building Workspace Manager ApiClient object for test user: {}",
                testUser.name);
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(server.workspaceManagerUri);

        apiClient.setAccessToken(userAccessToken.getTokenValue());

        apiClientsForTestUsers.put(testUser, apiClient);
        return apiClient;
    }
}
