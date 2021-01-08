package scripts.utils;

import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.DataReferenceDescription;
import bio.terra.workspace.model.DataRepoSnapshot;
import bio.terra.workspace.model.ReferenceTypeEnum;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class WorkspaceManagerServiceUtils {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceManagerServiceUtils.class);

    private WorkspaceManagerServiceUtils() {}

    /**
     * Build the Workspace Manager Service API client object for the given server specification.
     * It is setting the access token for the Test Runner SA specified in the given server specification.
     *
     * A Test Runner SA is a GCP SA with appropriate permissions / scopes to run all the client tests within this repo.
     * For example, to run resiliency tests against K8S infrastructure, you'll need a SA powerful enough to do things like list, read, and update.
     *
     * @param server the server we are testing against
     * @return the API client object
     */
    public static ApiClient getClientForTestRunnerSA(ServerSpecification server) throws IOException {
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

        return buildClient(accessToken, server);
    }

    /**
     * Build the Workspace Manager API client object for the given test user and server specifications.
     * The test user's token is always refreshed
     *
     * @param testUser the test user whose credentials are supplied to the API client object
     * @param server the server we are testing against
     * @return the API client object for this user
     */
    public static ApiClient getClientForTestUser(
            TestUserSpecification testUser, ServerSpecification server) throws IOException {
        AccessToken accessToken = null;

        // if no test user is specified, then return a client object without an access token set
        // this is useful if the caller wants to make ONLY unauthenticated calls
        if (testUser != null) {
            logger.debug(
                    "Fetching credentials and building Workspace Manager ApiClient object for test user: {}",
                    testUser.name);

            // refresh the user token
            GoogleCredentials userCredential =
                    AuthenticationUtils.getDelegatedUserCredential(
                            testUser, AuthenticationUtils.userLoginScopes);
            accessToken = AuthenticationUtils.getAccessToken(userCredential);
        }

        return buildClient(accessToken, server);
    }

    /**
     * Build the Workspace Manager API client object for the server specifications.
     * No access token is needed for this API client.
     *
     * @param server the server we are testing against
     * @return the API client object for this user
     */
    public static ApiClient getClientWithoutAccessToken(ServerSpecification server) throws IOException {
        return buildClient(null, server);
    }

    private static ApiClient buildClient(AccessToken accessToken, ServerSpecification server) throws IOException {
        if (Strings.isNullOrEmpty(server.workspaceManagerUri)) {
            throw new IllegalArgumentException("Workspace Manager Service URI cannot be empty");
        }

        // build the client object
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(server.workspaceManagerUri);

        if (accessToken != null) {
            apiClient.setAccessToken(accessToken.getTokenValue());
        }

        return apiClient;
    }
}
