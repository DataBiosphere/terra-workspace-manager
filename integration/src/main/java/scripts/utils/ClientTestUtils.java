package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.in;

import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.RoleBindingList;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.testscripts.ControlledGcsBucketLifecycle;

public class ClientTestUtils {

  public static final String TEST_BUCKET_NAME = "terra_wsm_test_resource";
  public static final String TEST_BQ_DATASET_NAME = "terra_wsm_test_dataset";
  public static final String TEST_BQ_DATASET_PROJECT = "terra-kernel-k8s";
  public static final String RESOURCE_NAME_PREFIX = "terratest";
  private static final Logger logger = LoggerFactory.getLogger(ClientTestUtils.class);

  // Required scopes for client tests include the usual login scopes and GCP scope.
  private static final List<String> TEST_USER_SCOPES =
      List.of("openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");

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
      // TODO(PF-657): TestRunner caches delegated credentials by TestUser, ignoring scopes. This
      //  should change, but for now I include all scopes we'll need on this user in the first call.
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

  public static Iam getGcpIamClient(TestUserSpecification testUser)
      throws GeneralSecurityException, IOException {
    return new Iam(
        GoogleNetHttpTransport.newTrustedTransport(),
        JacksonFactory.getDefaultInstance(),
        new HttpCredentialsAdapter(
            AuthenticationUtils.getDelegatedUserCredential(testUser, TEST_USER_SCOPES)));
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

  public static ControlledGcpResourceApi getControlledGcpResourceClient(
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
   * Get a result from a call that might throw an exception. Treat the exception as retryable,
   * sleep for a specific duration, and retry up to a fixed number of times. This structure
   * is useful for situations where we are waiting on a cloud IAM permission change to take effect.
   * @param supplier - code returning the result or throwing an exception
   * @param numTries - number of times to retry the operation
   * @param sleepDuration - sleep time between attempts
   * @param <T> - type of result
   * @return - result from supplier, the first time it doesn't throw
   * @throws InterruptedException
   */
  public static <T> T getWithRetryOnException(Supplier<T> supplier, int numTries, Duration sleepDuration) throws InterruptedException {
    T result = null;
    while (numTries > 0) {
      try {
        // read blob as second user
        result = supplier.get();
        break;
      } catch (Exception e) {
        numTries--;
        int sleepSeconds = 30;
        logger.info("Exception \"{}\". Waiting {} seconds for permissions to propagate. Tries remaining: {}", e.getMessage(), sleepSeconds, numTries);
        TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
      }
    }
    return result;
  }

  /**
   * An interface for an arbitrary workspace operation that throws an {@link ApiException}.
   */
  @FunctionalInterface
  public interface WorkspaceOperation<T> {

    T apply() throws ApiException;
  }

  /**
   * Polls a workspace API operation as long as the job is running.
   *
   * @param <T>                the result type of the async operation.
   * @param initialValue       the first result to use to poll
   * @param operation          a function for the workspace async operation to execute
   * @param jobReportExtractor a function for getting the {@link JobReport} from the result
   * @param pollInterval       how long to sleep between polls.
   */
  public static <T> T pollWhileRunning(
      T initialValue,
      WorkspaceOperation<T> operation,
      Function<T, JobReport> jobReportExtractor,
      Duration pollInterval) throws InterruptedException, ApiException {
    T result = initialValue;
    while (jobIsRunning(jobReportExtractor.apply(result))) {
      Thread.sleep(pollInterval.toMillis());
      result = operation.apply();
    }
    return result;
  }

  /** @return a generated unique resource name consisting of letters, numbers, and underscores. */
  public static String generateCloudResourceName() {
    String name = RESOURCE_NAME_PREFIX + UUID.randomUUID().toString();
    return name.replace("-", "_");
  }
}
