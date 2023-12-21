package scripts.utils;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledAzureResourceApi;
import bio.terra.workspace.api.ControlledFlexibleResourceApi;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.JobsApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.ErrorReport;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.JobReport.StatusEnum;
import bio.terra.workspace.model.RoleBindingList;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.dataproc.Dataproc;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientTestUtils {
  public static final String RESOURCE_NAME_PREFIX = "terratest";
  // We may want this to be a test parameter. It has to match what is in the config or in the helm
  public static final String TEST_WSM_APP = "TestWsmApp";
  // Required scopes for client tests include the usual login scopes and GCP scope.
  public static final List<String> TEST_USER_SCOPES =
      List.of("openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");
  private static final Logger logger = LoggerFactory.getLogger(ClientTestUtils.class);

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
      GoogleCredentials userCredential =
          AuthenticationUtils.getDelegatedUserCredential(testUser, TEST_USER_SCOPES);
      accessToken = AuthenticationUtils.getAccessToken(userCredential);
    }

    return buildClient(accessToken, server);
  }

  public static AIPlatformNotebooks getAIPlatformNotebooksClient(TestUserSpecification testUser)
      throws GeneralSecurityException, IOException {
    return new AIPlatformNotebooks(
        GoogleNetHttpTransport.newTrustedTransport(),
        JacksonFactory.getDefaultInstance(),
        new HttpCredentialsAdapter(
            AuthenticationUtils.getDelegatedUserCredential(testUser, TEST_USER_SCOPES)));
  }

  public static Dataproc getDataprocClient(TestUserSpecification testUser)
      throws GeneralSecurityException, IOException {
    return new Dataproc(
        GoogleNetHttpTransport.newTrustedTransport(),
        JacksonFactory.getDefaultInstance(),
        new HttpCredentialsAdapter(
            AuthenticationUtils.getDelegatedUserCredential(testUser, TEST_USER_SCOPES)));
  }

  public static Iam getGcpIamClient(TestUserSpecification testUser)
      throws GeneralSecurityException, IOException {
    return new Iam(
        GoogleNetHttpTransport.newTrustedTransport(),
        JacksonFactory.getDefaultInstance(),
        new HttpCredentialsAdapter(
            AuthenticationUtils.getDelegatedUserCredential(testUser, TEST_USER_SCOPES)));
  }

  public static Iam getGcpIamClientFromToken(AccessToken accessToken)
      throws GeneralSecurityException, IOException {
    return new Iam(
        GoogleNetHttpTransport.newTrustedTransport(),
        JacksonFactory.getDefaultInstance(),
        new HttpCredentialsAdapter(new GoogleCredentials(accessToken)));
  }

  public static Storage getGcpStorageClient(TestUserSpecification testUser, String projectId)
      throws IOException {
    GoogleCredentials userCredential =
        AuthenticationUtils.getDelegatedUserCredential(testUser, TEST_USER_SCOPES);
    StorageOptions options =
        StorageOptions.newBuilder().setCredentials(userCredential).setProjectId(projectId).build();
    return options.getService();
  }

  public static BigQuery getGcpBigQueryClient(TestUserSpecification testUser, String projectId)
      throws IOException {
    GoogleCredentials userCredential =
        AuthenticationUtils.getDelegatedUserCredential(testUser, TEST_USER_SCOPES);
    var options =
        BigQueryOptions.newBuilder().setCredentials(userCredential).setProjectId(projectId).build();
    return options.getService();
  }

  public static WorkspaceApi getWorkspaceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new WorkspaceApi(apiClient);
  }

  public static WorkspaceApi getWorkspaceClientFromToken(
      AccessToken accessToken, ServerSpecification server) throws IOException {
    final ApiClient apiClient = buildClient(accessToken, server);
    return new WorkspaceApi(apiClient);
  }

  public static ControlledGcpResourceApi getControlledGcpResourceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new ControlledGcpResourceApi(apiClient);
  }

  public static ControlledFlexibleResourceApi getControlledFlexResourceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new ControlledFlexibleResourceApi(apiClient);
  }

  public static ControlledAzureResourceApi getControlledAzureResourceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new ControlledAzureResourceApi(apiClient);
  }

  public static ReferencedGcpResourceApi getReferencedGcpResourceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new ReferencedGcpResourceApi(apiClient);
  }

  public static ResourceApi getResourceClient(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new ResourceApi(apiClient);
  }

  public static JobsApi getJobsClient(TestUserSpecification testUser, ServerSpecification server)
      throws IOException {
    final ApiClient apiClient = getClientForTestUser(testUser, server);
    return new JobsApi(apiClient);
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

  /**
   * Polls a workspace API operation as long as the job is running.
   *
   * @param <T> the result type of the async operation.
   * @param initialValue the first result to use to poll
   * @param operation a function for the workspace async operation to execute
   * @param jobReportExtractor a function for getting the {@link JobReport} from the result
   * @param pollInterval how long to sleep between polls.
   */
  public static <T> T pollWhileRunning(
      T initialValue,
      WorkspaceOperation<T> operation,
      Function<T, JobReport> jobReportExtractor,
      Duration pollInterval)
      throws InterruptedException, ApiException {
    T result = initialValue;
    while (jobIsRunning(jobReportExtractor.apply(result))) {
      Thread.sleep(pollInterval.toMillis());
      result = operation.apply();
    }
    return result;
  }

  /**
   * @return a generated unique resource name consisting of letters, numbers, and underscores.
   */
  public static String generateCloudResourceName() {
    String name = RESOURCE_NAME_PREFIX + UUID.randomUUID();
    return name.replace("-", "_");
  }

  /**
   * Check for job success with logging. On failure, this will assert. On success, it will return.
   * This replaces use of the simple assert for success, since that hides the failure message,
   * making debugging harder.
   *
   * @param operation string to present in the log message
   * @param jobReport jobReport from the operation result
   * @param errorReport errorReport from the operation result - may be null if no error
   */
  public static void assertJobSuccess(
      String operation, JobReport jobReport, @Nullable ErrorReport errorReport) {
    if (jobReport.getStatus() == StatusEnum.SUCCEEDED) {
      assertNull(errorReport);
      logger.info("Operation {} succeeded", operation);
    } else {
      if (errorReport == null) {
        logger.error("Operation {} failed - no error report", operation);
      } else {
        logger.error("Operation {} failed. Error report: {}", operation, errorReport);
        fail("Failed operation " + operation);
      }
    }
  }

  /**
   * Check Optional's value is present and return it, or else fail an assertion.
   *
   * @param optional - Optional expression
   * @param <T> - value type of optional
   * @return - value of optional, if present
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public static <T> T assertPresent(Optional<T> optional, @Nullable String message) {
    assertTrue(
        optional.isPresent(), Optional.ofNullable(message).orElse("Optional value not present."));
    return optional.get();
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public static <T> T assertPresent(Optional<T> optional) {
    return assertPresent(optional, null);
  }

  @FunctionalInterface
  public interface SupplierWithException<T> {
    T get() throws Exception;
  }

  /** An interface for an arbitrary workspace operation that throws an {@link ApiException}. */
  @FunctionalInterface
  public interface WorkspaceOperation<T> {
    T apply() throws ApiException;
  }

  // TEMPORARY MEASURE UNTIL WE DO THE WAITING IN WSM
  public static void grantRoleWaitForPropagation(
      WorkspaceApi workspaceApi,
      UUID workspaceUuid,
      String gcpProjectId,
      TestUserSpecification grantee,
      IamRole roleToGrant)
      throws Exception {
    // Make sure it this will work
    assertTrue(roleToGrant != IamRole.APPLICATION && roleToGrant != IamRole.DISCOVERER);

    grantRole(workspaceApi, workspaceUuid, grantee, roleToGrant);

    workspaceRoleWaitForPropagation(grantee, gcpProjectId);
  }

  public static void grantRole(
      WorkspaceApi workspaceApi,
      UUID workspaceUuid,
      TestUserSpecification grantee,
      IamRole roleToGrant)
      throws Exception {
    // Have WSM do the grant
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(grantee.userEmail), workspaceUuid, roleToGrant);
    logger.info("Added {} as {} to workspace {}", grantee.userEmail, roleToGrant, workspaceUuid);
  }

  public static void workspaceRoleWaitForPropagation(
      TestUserSpecification grantee, String gcpProjectId) throws Exception {
    Storage granteeStorage = getGcpStorageClient(grantee, gcpProjectId);

    // Wait for the grantee to have storage.bucket.list permission,
    // indicating that the grant has been propagated to the project.
    RetryUtils.getWithRetryOnException(granteeStorage::list);
  }

  /**
   * Revoke a workspace role and wait for propagation
   *
   * @param workspaceApi api access
   * @param workspaceUuid workspace
   * @param gcpProjectId project
   * @param testUser user to revoke
   * @param roleToRevoke role to revoke
   * @return true if revoke completed; false if non-forbidden error is thrown. Users of this method
   *     should assert the result.
   * @throws Exception general
   */
  public static boolean revokeRoleWaitForPropagation(
      WorkspaceApi workspaceApi,
      UUID workspaceUuid,
      String gcpProjectId,
      TestUserSpecification testUser,
      IamRole roleToRevoke)
      throws Exception {
    // Make sure it this will work
    assertTrue(roleToRevoke != IamRole.APPLICATION && roleToRevoke != IamRole.DISCOVERER);

    workspaceApi.removeRole(workspaceUuid, roleToRevoke, testUser.userEmail);
    Storage storage = getGcpStorageClient(testUser, gcpProjectId);

    // Wait for the testUser to lose storage.bucket.list permission,
    // indicating that the revoke has been propagated to the project.
    return RetryUtils.getWithRetryOnException(() -> testForbiddenStorageList(storage, testUser));
  }

  private static boolean testForbiddenStorageList(Storage storage, TestUserSpecification testUser)
      throws Exception {
    try {
      storage.list();
      logger.info("User {} still has access to the project", testUser.userEmail);
      throw new RuntimeException("User still has access to the project: " + testUser.userEmail);
    } catch (StorageException e) {
      if (e.getCode() == SC_FORBIDDEN) {
        return true;
      }
      throw e;
    } catch (Exception e) {
      logger.info("Caught unexpected exception", e);
      return false;
    }
  }
}
