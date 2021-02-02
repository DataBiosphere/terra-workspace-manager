package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.DataReferenceDescription;
import bio.terra.workspace.model.DataReferenceList;
import bio.terra.workspace.model.DataRepoSnapshot;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ReferenceTypeEnum;
import bio.terra.workspace.model.RoleBindingList;
import com.google.api.client.http.HttpStatusCodes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import scripts.testscripts.EnumerateDataReferences;

public class ClientTestUtils {

    public static final String DATA_REFERENCE_NAME_PREFIX = "REF_";
    public static final String TEST_SNAPSHOT = "97b5559a-2f8f-4df3-89ae-5a249173ee0c";
    public static final String TERRA_DATA_REPO_INSTANCE = "terra";

    private static final Logger logger = LoggerFactory.getLogger(ClientTestUtils.class);

    private ClientTestUtils() {}

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
    public static @NotNull ApiClient getClientForTestRunnerSA(@NotNull ServerSpecification server) throws IOException {
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
    public static @NotNull ApiClient getClientForTestUser(
            @Nullable TestUserSpecification testUser, @NotNull ServerSpecification server) throws IOException {
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

    public static @NotNull WorkspaceApi getWorkspaceClient(TestUserSpecification testUser, @NotNull ServerSpecification server) throws IOException {
        final ApiClient apiClient = getClientForTestUser(testUser, server);
        return new WorkspaceApi(apiClient);
    }

    /**
     * Build the Workspace Manager API client object for the server specifications.
     * No access token is needed for this API client.
     *
     * @param server the server we are testing against
     * @return the API client object for this user
     */
    public static @NotNull ApiClient getClientWithoutAccessToken(@NotNull ServerSpecification server) throws IOException {
        return buildClient(null, server);
    }

    private static @NotNull ApiClient buildClient(
        @Nullable AccessToken accessToken, @NotNull ServerSpecification server) throws IOException {
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

    public static void assertHttpSuccess(@NotNull WorkspaceApi workspaceApi, String label) {
        int httpCode = workspaceApi.getApiClient().getStatusCode();
        logger.debug("{} HTTP code: {}", label, httpCode);
        assertThat(HttpStatusCodes.isSuccess(httpCode), equalTo(true));
    }

    /**
     * Return a globally unique data reference name starting with the constant prefix.
     * This will include a UUID reformatted to meet the rules for data reference names
     * (just replacing hyphens with underscores). This method is useful when creating references
     * on the same workspace from multiple threads.
     * @return
     */
    public static @NotNull String getUniqueDataReferenceName() {
        return DATA_REFERENCE_NAME_PREFIX +
            UUID.randomUUID()
                .toString()
                .replace("-", "_");
    }

    public static DataRepoSnapshot getTestDataRepoSnapshot() {
      return new DataRepoSnapshot()
          .snapshot(TEST_SNAPSHOT)
          .instanceName(TERRA_DATA_REPO_INSTANCE);
    }

    public static CreateDataReferenceRequestBody getTestCreateDataReferenceRequestBody() {
      return new CreateDataReferenceRequestBody()
          .name(getUniqueDataReferenceName())
          .cloningInstructions(CloningInstructionsEnum.REFERENCE)
          .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
          .reference(getTestDataRepoSnapshot());
    }

    public static List<DataReferenceDescription> getDataReferenceDescriptions(
        UUID workspaceId, @NotNull WorkspaceApi workspaceApi,
        int offset, int limit) throws ApiException {
      final DataReferenceList dataReferenceListFirstPage = workspaceApi.enumerateReferences(
          workspaceId, offset, limit);
      return dataReferenceListFirstPage.getResources();
    }

    /**
     * True if the role binding list contains a binding for a given user and Iam Role.
     * @param roleBindings - list of role bindings, as retrieved via getRoles()
     * @param userEmail - user to check for
     * @param role - role to check
     * @return
     */
  public static boolean containsBinding(
      @NotNull RoleBindingList roleBindings,
      String userEmail,
      IamRole role) {
    return roleBindings.stream()
        .anyMatch(rb ->
            rb.getRole() == role && rb.getMembers().contains(userEmail));
  }
}
