package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static scripts.utils.CommonResourceFieldsUtil.makeControlledResourceCommonFields;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.DeleteControlledGcpAiNotebookInstanceRequest;
import bio.terra.workspace.model.DeleteControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.GcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.model.GcpAiNotebookInstanceVmImage;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ManagedBy;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import com.google.api.services.notebooks.v1.model.Instance;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotebookUtils {
  private static final Logger logger = LoggerFactory.getLogger(NotebookUtils.class);
  private static final int ASSERT_PROXY_URL_RETRY_MAX = 40;
  private static final int ASSERT_PROXY_URL_RETRY_SECONDS = 15;

  /**
   * Create and return a private AI Platform Notebook controlled resource with constant values. This
   * method calls the asynchronous creation endpoint and polls until the creation job completes.
   */
  public static CreatedControlledGcpAiNotebookInstanceResult makeControlledNotebookUserPrivate(
      UUID workspaceUuid,
      @Nullable String instanceId,
      @Nullable String location,
      ControlledGcpResourceApi resourceApi,
      @Nullable String testValue,
      @Nullable String postStartupScript)
      throws ApiException, InterruptedException {
    var resourceName = RandomStringUtils.randomAlphabetic(6);
    // Fill out the minimum required fields to arbitrary values.
    var creationParameters =
        new GcpAiNotebookInstanceCreationParameters()
            .instanceId(instanceId)
            .location(location)
            .machineType("e2-standard-2")
            .vmImage(
                new GcpAiNotebookInstanceVmImage()
                    .projectId("deeplearning-platform-release")
                    .imageFamily("r-latest-cpu-experimental"))
            .metadata(
                Map.of(
                    "terra-test-value",
                    Optional.ofNullable(testValue).orElse(""),
                    "terra-gcp-notebook-resource-name",
                    resourceName));
    if (!StringUtils.isEmpty(postStartupScript)) {
      creationParameters.postStartupScript(postStartupScript);
    }

    var commonParameters =
        makeControlledResourceCommonFields(
            resourceName,
            /* privateUser= */ null,
            CloningInstructionsEnum.NOTHING,
            ManagedBy.USER,
            AccessScope.PRIVATE_ACCESS);

    var body =
        new CreateControlledGcpAiNotebookInstanceRequestBody()
            .aiNotebookInstance(creationParameters)
            .common(commonParameters)
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    var creationResult = resourceApi.createAiNotebookInstance(body, workspaceUuid);
    String creationJobId = creationResult.getJobReport().getId();
    creationResult =
        ClientTestUtils.pollWhileRunning(
            creationResult,
            () -> resourceApi.getCreateAiNotebookInstanceResult(workspaceUuid, creationJobId),
            CreatedControlledGcpAiNotebookInstanceResult::getJobReport,
            Duration.ofSeconds(10));
    ClientTestUtils.assertJobSuccess(
        "create ai notebook", creationResult.getJobReport(), creationResult.getErrorReport());
    return creationResult;
  }

  /**
   * Delete an AI Platform notebook. This endpoint calls the asynchronous endpoint and polls until
   * the delete job completes.
   */
  public static void deleteControlledNotebookUserPrivate(
      UUID workspaceUuid, UUID resourceId, ControlledGcpResourceApi resourceApi)
      throws ApiException, InterruptedException {
    var body =
        new DeleteControlledGcpAiNotebookInstanceRequest()
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    var deletionResult = resourceApi.deleteAiNotebookInstance(body, workspaceUuid, resourceId);
    String deletionJobId = deletionResult.getJobReport().getId();
    deletionResult =
        ClientTestUtils.pollWhileRunning(
            deletionResult,
            () -> resourceApi.getDeleteAiNotebookInstanceResult(workspaceUuid, deletionJobId),
            DeleteControlledGcpAiNotebookInstanceResult::getJobReport,
            Duration.ofSeconds(10));
    ClientTestUtils.assertJobSuccess(
        "delete ai notebook", deletionResult.getJobReport(), deletionResult.getErrorReport());
  }

  /**
   * Check whether the user has access to the Notebook through the proxy with a service account.
   *
   * <p>We can't directly test that we can go through the proxy to the Jupyter notebook without a
   * real Google user auth flow, so we check the necessary ingredients instead.
   */
  public static boolean userHasProxyAccess(
      CreatedControlledGcpAiNotebookInstanceResult createdNotebook,
      TestUserSpecification user,
      String projectId)
      throws GeneralSecurityException, IOException {

    String instanceName =
        String.format(
            "projects/%s/locations/%s/instances/%s",
            createdNotebook.getAiNotebookInstance().getAttributes().getProjectId(),
            createdNotebook.getAiNotebookInstance().getAttributes().getLocation(),
            createdNotebook.getAiNotebookInstance().getAttributes().getInstanceId());
    AIPlatformNotebooks userNotebooks = ClientTestUtils.getAIPlatformNotebooksClient(user);
    Instance instance;
    try {
      instance = userNotebooks.projects().locations().instances().get(instanceName).execute();
    } catch (GoogleJsonResponseException googleException) {
      // If we get a 403 or 404 when fetching the instance, the user does not have access.
      if (googleException.getStatusCode() == HttpStatus.SC_FORBIDDEN
          || googleException.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return false;
      } else {
        // If a different status code is thrown instead, rethrow here as that's an unexpected error.
        throw googleException;
      }
    }
    // Test that the user has access to the notebook with a service account through proxy mode.
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(
        "Notebook has correct proxy mode access",
        instance.getMetadata(),
        Matchers.hasEntry("proxy-mode", "service_" + "account"));

    // The user needs to have the actAs permission on the service account.
    String actAsPermission = "iam.serviceAccounts.actAs";
    String serviceAccountName =
        String.format("projects/%s/serviceAccounts/%s", projectId, instance.getServiceAccount());
    List<String> maybePermissionsList =
        ClientTestUtils.getGcpIamClient(user)
            .projects()
            .serviceAccounts()
            .testIamPermissions(
                serviceAccountName,
                new TestIamPermissionsRequest().setPermissions(List.of(actAsPermission)))
            .execute()
            .getPermissions();
    // GCP returns null rather than an empty list when a user does not have any permissions
    return Optional.ofNullable(maybePermissionsList)
        .map(list -> list.contains(actAsPermission))
        .orElse(false);
  }

  /**
   * Asserts that the notebook instance contains proxy url. It usually takes 1-2 min for the proxy
   * url to be set.
   */
  public static void assertInstanceHasProxyUrl(
      AIPlatformNotebooks userNotebooks, String instanceName) throws Exception {
    for (int retryCount = 0; retryCount < ASSERT_PROXY_URL_RETRY_MAX; retryCount++) {
      try {
        String proxyUrl =
            userNotebooks
                .projects()
                .locations()
                .instances()
                .get(instanceName)
                .execute()
                .getProxyUri();
        if (proxyUrl != null) {
          return;
        }
        logger.info("Notebook proxy url is null, retry");
      } catch (GoogleJsonResponseException e) {
        // Retry 403s as permissions may take time to propagate, but do not retry if it's
        // any other IO exception.
        if (e.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
          logger.info("Fails to fetch notebook proxy url due to 403, retry", e);
        } else {
          logger.info("Fails to fetch notebook proxy url, do not retry", e);
          throw e;
        }
      } catch (Exception e) {
        logger.info("Fails to fetch notebook proxy url, do not retry", e);
        throw e;
      }
      // If we are here, we are retrying
      logger.info(
          "Retrying getProxyUrl after {} seconds; retry {} of {}",
          ASSERT_PROXY_URL_RETRY_SECONDS,
          retryCount + 1,
          ASSERT_PROXY_URL_RETRY_MAX);
      TimeUnit.SECONDS.sleep(ASSERT_PROXY_URL_RETRY_SECONDS);
    }
    throw new RuntimeException("Retries of getProxyUrl are exhausted");
  }
}
