package scripts.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.CommonResourceFieldsUtil.makeControlledResourceCommonFields;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateControlledGcpDataprocClusterRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpDataprocClusterResult;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.DeleteControlledGcpDataprocClusterRequest;
import bio.terra.workspace.model.DeleteControlledGcpDataprocClusterResult;
import bio.terra.workspace.model.GcpDataprocClusterCreationParameters;
import bio.terra.workspace.model.GcpDataprocClusterInstanceGroupConfig;
import bio.terra.workspace.model.GcpDataprocClusterLifecycleConfig;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ManagedBy;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.dataproc.Dataproc;
import com.google.api.services.dataproc.model.Cluster;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
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
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataprocUtils {
  private static final Logger logger = LoggerFactory.getLogger(DataprocUtils.class);
  private static final int ASSERT_PROXY_URL_RETRY_MAX = 40;
  private static final int ASSERT_PROXY_URL_RETRY_SECONDS = 15;

  // Key used to get the proxy url for the JupyterLab optional component.
  private static final String JUPYTER_LAB_HTTP_PORT_KEY = "JupyterLab";

  /**
   * Create and return a private Dataproc Cluster controlled resource with constant values. This
   * method calls the asynchronous creation endpoint and polls until the creation job completes.
   */
  public static CreatedControlledGcpDataprocClusterResult createPrivateDataprocCluster(
      UUID workspaceUuid,
      @Nullable String clusterId,
      @Nullable String region,
      @Nullable String startupScriptUrl,
      ControlledGcpResourceApi resourceApi)
      throws Exception {

    String stagingBucketName = String.format("dataproc-staging-%s", UUID.randomUUID());
    String tempBucketName = String.format("dataproc-temp-%s", UUID.randomUUID());

    CreatedControlledGcpGcsBucket stagingBucket =
        GcsBucketUtils.makeControlledGcsBucket(
            resourceApi,
            workspaceUuid,
            stagingBucketName,
            /* bucketName= */ stagingBucketName,
            AccessScope.PRIVATE_ACCESS,
            ManagedBy.USER,
            CloningInstructionsEnum.NOTHING,
            null);
    CreatedControlledGcpGcsBucket tempBucket =
        GcsBucketUtils.makeControlledGcsBucket(
            resourceApi,
            workspaceUuid,
            tempBucketName,
            /* bucketName= */ tempBucketName,
            AccessScope.PRIVATE_ACCESS,
            ManagedBy.USER,
            CloningInstructionsEnum.NOTHING,
            null);

    assertEquals(stagingBucketName, stagingBucket.getGcpBucket().getAttributes().getBucketName());
    assertEquals(tempBucketName, tempBucket.getGcpBucket().getAttributes().getBucketName());

    var resourceName = RandomStringUtils.randomAlphabetic(6);
    var creationParameters =
        new GcpDataprocClusterCreationParameters()
            .clusterId(clusterId)
            .region(region)
            .configBucket(stagingBucket.getResourceId())
            .tempBucket(tempBucket.getResourceId())
            .managerNodeConfig(
                new GcpDataprocClusterInstanceGroupConfig()
                    .numInstances(1)
                    .machineType("n2-standard-2"))
            .primaryWorkerConfig(
                new GcpDataprocClusterInstanceGroupConfig()
                    .numInstances(2)
                    .machineType("n2-standard-2"))
            .secondaryWorkerConfig(
                new GcpDataprocClusterInstanceGroupConfig()
                    .numInstances(2)
                    .machineType("n2-standard-2"))
            .components(List.of("JUPYTER"))
            .lifecycleConfig(new GcpDataprocClusterLifecycleConfig().idleDeleteTtl("3600s"));

    if (startupScriptUrl != null) {
      creationParameters.metadata(Map.of("startup-script-url", startupScriptUrl));
    }

    var commonParameters =
        makeControlledResourceCommonFields(
            resourceName,
            /* privateUser= */ null,
            CloningInstructionsEnum.NOTHING,
            ManagedBy.USER,
            AccessScope.PRIVATE_ACCESS);

    var body =
        new CreateControlledGcpDataprocClusterRequestBody()
            .dataprocCluster(creationParameters)
            .common(commonParameters)
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    var creationResult = resourceApi.createDataprocCluster(body, workspaceUuid);
    String creationJobId = creationResult.getJobReport().getId();
    creationResult =
        ClientTestUtils.pollWhileRunning(
            creationResult,
            () -> resourceApi.getCreateDataprocClusterResult(workspaceUuid, creationJobId),
            CreatedControlledGcpDataprocClusterResult::getJobReport,
            Duration.ofSeconds(10));
    ClientTestUtils.assertJobSuccess(
        "create dataproc cluster", creationResult.getJobReport(), creationResult.getErrorReport());
    return creationResult;
  }

  /**
   * Delete a Dataproc Cluster. This endpoint calls the asynchronous endpoint and polls until the
   * delete job completes.
   */
  public static void deleteControlledDataprocCluster(
      UUID workspaceUuid, UUID resourceId, ControlledGcpResourceApi resourceApi)
      throws ApiException, InterruptedException {
    var body =
        new DeleteControlledGcpDataprocClusterRequest()
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    var deletionResult = resourceApi.deleteDataprocCluster(body, workspaceUuid, resourceId);
    String deletionJobId = deletionResult.getJobReport().getId();
    deletionResult =
        ClientTestUtils.pollWhileRunning(
            deletionResult,
            () -> resourceApi.getDeleteDataprocClusterResult(workspaceUuid, deletionJobId),
            DeleteControlledGcpDataprocClusterResult::getJobReport,
            Duration.ofSeconds(10));
    ClientTestUtils.assertJobSuccess(
        "delete dataproc cluster", deletionResult.getJobReport(), deletionResult.getErrorReport());
  }

  /**
   * Check whether the user has access to the Jupyerlab component gateway proxy.
   *
   * <p>We can't directly test that we can go through the proxy to Jupyterlab without a real Google
   * user auth flow, so instead we check that the proxy url is available and that the user can both
   * impersonate its service account and has use permission on the cluster.
   */
  public static boolean userHasProxyAccess(
      CreatedControlledGcpDataprocClusterResult createdCluster,
      TestUserSpecification user,
      String projectId)
      throws GeneralSecurityException, IOException {

    String region = createdCluster.getDataprocCluster().getAttributes().getRegion();
    String clusterId = createdCluster.getDataprocCluster().getAttributes().getClusterId();
    Dataproc dataproc = ClientTestUtils.getDataprocClient(user);

    // The user needs to have get permission on the cluster
    Cluster cluster;
    try {
      cluster =
          dataproc.projects().regions().clusters().get(projectId, region, clusterId).execute();
    } catch (GoogleJsonResponseException googleException) {
      // If we get a 403 or 404 when fetching the cluster, the user does not have access.
      if (googleException.getStatusCode() == HttpStatus.SC_FORBIDDEN
          || googleException.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return false;
      } else {
        // If a different status code is thrown instead, rethrow here as that's an unexpected error.
        throw googleException;
      }
    }

    // The user needs to have the actAs permission on the service account.
    String actAsPermission = "iam.serviceAccounts.actAs";
    String serviceAccountName =
        String.format(
            "projects/%s/serviceAccounts/%s",
            projectId, cluster.getConfig().getGceClusterConfig().getServiceAccount());
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
    boolean hasActAsPermission =
        Optional.ofNullable(maybePermissionsList)
            .map(list -> list.contains(actAsPermission))
            .orElse(false);

    // The user needs to have use permission on the cluster
    String clusterUsePermission = "dataproc.clusters.use";
    String clusterName =
        String.format("projects/%s/regions/%s/clusters/%s", projectId, region, clusterId);
    List<String> clusterPermissionsList =
        dataproc
            .projects()
            .regions()
            .clusters()
            .testIamPermissions(
                clusterName,
                new com.google.api.services.dataproc.model.TestIamPermissionsRequest()
                    .setPermissions(List.of(clusterUsePermission)))
            .execute()
            .getPermissions();
    boolean hasClusterUsePermission =
        Optional.ofNullable(clusterPermissionsList)
            .map(list -> list.contains(clusterUsePermission))
            .orElse(false);

    return hasClusterUsePermission && hasActAsPermission;
  }

  /** Asserts that the Dataproc cluster contains proxy url. */
  public static void assertClusterHasProxyUrl(
      Dataproc dataproc, String projectId, String region, String clusterName) throws Exception {
    for (int retryCount = 0; retryCount < ASSERT_PROXY_URL_RETRY_MAX; retryCount++) {
      try {
        String proxyUrl =
            dataproc
                .projects()
                .regions()
                .clusters()
                .get(projectId, region, clusterName)
                .execute()
                .getConfig()
                .getEndpointConfig()
                .getHttpPorts()
                .get(JUPYTER_LAB_HTTP_PORT_KEY);
        if (proxyUrl != null) {
          return;
        }
        logger.info("Cluster JupyterLab proxy url is null, retry");
      } catch (GoogleJsonResponseException e) {
        // Retry 403s as permissions may take time to propagate, but do not retry if it's
        // any other IO exception.
        if (e.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
          logger.info("Failed to fetch Cluster JupyterLab proxy url due to 403, retry", e);
        } else {
          logger.info("Failed to fetch Cluster JupyterLab proxy url, do not retry", e);
          throw e;
        }
      } catch (Exception e) {
        logger.info("Failed to fetch Cluster JupyterLab proxy url, do not retry", e);
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
