package scripts.utils;

import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.DataReferenceDescription;
import bio.terra.workspace.model.DataReferenceList;
import bio.terra.workspace.model.DataRepoSnapshot;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ReferenceTypeEnum;
import bio.terra.workspace.model.RoleBindingList;
import com.google.api.client.http.HttpStatusCodes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ClientTestUtils {

  public static final String DATA_REFERENCE_NAME_PREFIX = "REF_";
  public static final String TEST_SNAPSHOT = "97b5559a-2f8f-4df3-89ae-5a249173ee0c";
  public static final String TERRA_DATA_REPO_INSTANCE = "terra";
  public static final String TEST_BUCKET_NAME = "terra_wsm_test_resource";
  public static final String TEST_BQ_DATASET_NAME = "terra_wsm_test_dataset";
  public static final String TEST_BQ_DATASET_PROJECT = "terra-kernel-k8s";
  public static final String BUCKET_NAME_PREFIX = "terratest";
  private static final Logger logger = LoggerFactory.getLogger(ClientTestUtils.class);

  // Required scopes for client tests include the usual login scopes.
  // We need additional scopes for validating access to cloud resources,
  // including:
  // - bigquery
  // - gcs
  private static final List<String> testUserScopes =
      List.of(
          "openid",
          "email",
          "profile",
          "https://www.googleapis.com/auth/bigquery",
          "https://www.googleapis.com/auth/devstorage.full_control");

  private ClientTestUtils() {}

  /**
   * Build the Workspace Manager Service API client object for the given server specification. It is
   * setting the access token for the Test Runner SA specified in the given server specification.
   *
   * <p>A Test Runner SA is a GCP SA with appropriate permissions / scopes to run all the client
   * tests within this repo. For example, to run resiliency tests against K8S infrastructure, you'll
   * need a SA powerful enough to do things like list, read, and update.
   *
   * @param server the server we are testing against
   * @return the API client object
   */
  public static ApiClient getClientForTestRunnerSA(ServerSpecification server) throws IOException {
    if (server.testRunnerServiceAccount == null) {
      throw new IllegalArgumentException(
          "Workspace Manager Service client service account is required");
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
   * Build the Workspace Manager API client object for the given test user and server
   * specifications. The test user's token is always refreshed
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

  public static WorkspaceApi getWorkspaceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new WorkspaceApi(apiClient);
  }

  public static ControlledGcpResourceApi getControlledGpcResourceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new ControlledGcpResourceApi(apiClient);
  }

  public static ReferencedGcpResourceApi getReferencedGpcResourceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new ReferencedGcpResourceApi(apiClient);
  }

  public static ResourceApi getResourceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new ResourceApi(apiClient);
  }

  /**
   * Build the Workspace Manager API client object for the server specifications. No access token is
   * needed for this API client.
   *
   * @param server the server we are testing against
   * @return the API client object for this user
   */
  public static ApiClient getClientWithoutAccessToken(ServerSpecification server)
      throws IOException {
    return buildClient(null, server);
  }

  private static ApiClient buildClient(
      @Nullable AccessToken accessToken, ServerSpecification server) throws IOException {
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

  public static void assertHttpSuccess(WorkspaceApi workspaceApi, String label) {
    int httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.debug("{} HTTP code: {}", label, httpCode);
    assertThat(HttpStatusCodes.isSuccess(httpCode), equalTo(true));
  }

  /**
   * Return a globally unique data reference name starting with the constant prefix. This will
   * include a UUID reformatted to meet the rules for data reference names (just replacing hyphens
   * with underscores). This method is useful when creating references on the same workspace from
   * multiple threads.
   *
   * @return unique data reference name
   */
  public static String getUniqueDataReferenceName() {
    String name = DATA_REFERENCE_NAME_PREFIX + UUID.randomUUID().toString();
    return name.replace("-", "_");
  }

  public static DataRepoSnapshot getTestDataRepoSnapshot() {
    return new DataRepoSnapshot().snapshot(TEST_SNAPSHOT).instanceName(TERRA_DATA_REPO_INSTANCE);
  }

  public static CreateDataReferenceRequestBody getTestCreateDataReferenceRequestBody() {
    return new CreateDataReferenceRequestBody()
        .name(getUniqueDataReferenceName())
        .cloningInstructions(CloningInstructionsEnum.REFERENCE)
        .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
        .reference(getTestDataRepoSnapshot());
  }

  public static List<DataReferenceDescription> getDataReferenceDescriptions(
      UUID workspaceId, WorkspaceApi workspaceApi, int offset, int limit) throws ApiException {
    final DataReferenceList dataReferenceListFirstPage =
        workspaceApi.enumerateReferences(workspaceId, offset, limit);
    return dataReferenceListFirstPage.getResources();
  }

  /**
   * Checks if a user email is in a role binding list
   *
   * @param roleBindings - list of role bindings, as retrieved via getRoles()
   * @param userEmail - user to check for
   * @param role - role to check
   * @return True if the role binding list contains a binding for a given user and Iam Role.
   */
  public static boolean containsBinding(
      RoleBindingList roleBindings, String userEmail, IamRole role) {
    return roleBindings.stream()
        .anyMatch(rb -> rb.getRole() == role && rb.getMembers().contains(userEmail));
  }

  public static boolean jobIsRunning(JobReport jobReport) {
    return jobReport.getStatus().equals(JobReport.StatusEnum.RUNNING);
  }

  /** @return unique test bucket name */
  public static String getTestBucketName() {
    String name = BUCKET_NAME_PREFIX + UUID.randomUUID().toString();
    return name.replace("-", "_");
  }
}
