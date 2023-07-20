package bio.terra.workspace.service.danglingresource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.cloudres.google.dataproc.DataprocCow;
import bio.terra.workspace.app.configuration.external.DanglingResourceCleanupConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterResource;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.services.dataproc.model.Operation;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("connectedPlus")
public class DanglingResourceCleanupServiceTest extends BaseConnectedTest {
  private Workspace workspace;
  private final int ASSERT_RESOURCE_CLEANUP_RETRY_COUNT = 40;
  private final int ASSERT_RESOURCE_CLEANUP_RETRY_SECONDS = 15;

  @Autowired WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired WorkspaceService workspaceService;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired SamService samService;
  @Autowired ControlledResourceService controlledResourceService;
  @Autowired DanglingResourceCleanupConfiguration danglingResourceCleanupConfiguration;
  @Autowired DanglingResourceCleanupService danglingResourceCleanupService;
  @Autowired ResourceDao resourceDao;
  @Autowired CrlService crlService;
  private UUID stagingBucketUuid;
  private UUID tempBucketUuid;

  /** Set up default workspace */
  @BeforeEach
  public void setup() throws Exception {
    // The cleanup cronjob will not run because it's initially disabled via configuration, but for
    // these tests we can enable it and manually trigger cleanup. No gap between runs to ensure
    // tests don't interfere with each other.
    danglingResourceCleanupConfiguration.setEnabled(true);
    danglingResourceCleanupConfiguration.setPollingInterval(Duration.ZERO);
    workspace =
        workspaceUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());

    // Create staging and temp controlled resource buckets
    String stagingBucketResourceName = TestUtils.appendRandomNumber("dataproc-staging-bucket");
    String stagingBucketCloudName = TestUtils.appendRandomNumber("dataproc-staging-bucket");
    ApiGcpGcsBucketResource stagingBucketResource =
        mockMvcUtils
            .createControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                workspace.getWorkspaceId(),
                stagingBucketResourceName,
                stagingBucketCloudName,
                /*location*/ null,
                /*storageClass*/ null,
                /*lifecycle*/ null)
            .getGcpBucket();

    String tempBucketResourceName = TestUtils.appendRandomNumber("dataproc-temp-bucket");
    String tempBucketCloudName = TestUtils.appendRandomNumber("dataproc-temp-bucket");
    ApiGcpGcsBucketResource tempBucketResource =
        mockMvcUtils
            .createControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                workspace.getWorkspaceId(),
                tempBucketResourceName,
                tempBucketCloudName,
                /*location*/ null,
                /*storageClass*/ null,
                /*lifecycle*/ null)
            .getGcpBucket();

    stagingBucketUuid = stagingBucketResource.getMetadata().getResourceId();
    tempBucketUuid = tempBucketResource.getMetadata().getResourceId();
  }

  /**
   * Delete workspace. Doing this outside of test bodies ensures cleanup runs even if tests fail.
   */
  @AfterEach
  public void cleanup() {
    workspaceService.deleteWorkspace(workspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void cleanupResourcesSuppressExceptions_cleansDanglingResources_succeeds() throws Exception {

    // Create a controlled dataproc cluster
    ApiGcpDataprocClusterResource cluster =
        mockMvcUtils
            .createDataprocCluster(
                userAccessUtils.defaultUserAuthRequest(),
                workspace.getWorkspaceId(),
                "asia-east1",
                stagingBucketUuid,
                tempBucketUuid)
            .getDataprocCluster();

    // Create a controlled gce instance
    ApiGcpGceInstanceResource instance =
        mockMvcUtils
            .createGceInstance(
                userAccessUtils.defaultUserAuthRequest(), workspace.getWorkspaceId(), null)
            .getGceInstance();

    // Create a controlled gcs bucket
    String bucketResourceName = TestUtils.appendRandomNumber("my-bucket");
    String bucketCloudName = TestUtils.appendRandomNumber("my-bucket");
    ApiGcpGcsBucketResource bucket =
        mockMvcUtils
            .createControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                workspace.getWorkspaceId(),
                bucketResourceName,
                bucketCloudName, /*location*/
                null,
                /*storageClass*/ null,
                /*lifecycle*/ null)
            .getGcpBucket();

    // Directly delete the cluster
    DataprocCow dataprocCow = crlService.getDataprocCow();
    ClusterName clusterName =
        ClusterName.builder()
            .projectId(cluster.getAttributes().getProjectId())
            .region(cluster.getAttributes().getRegion())
            .name(cluster.getAttributes().getClusterId())
            .build();
    try {
      OperationCow<Operation> deleteOperation =
          dataprocCow
              .regionOperations()
              .operationCow(dataprocCow.clusters().delete(clusterName).execute());
      GcpUtils.pollAndRetry(deleteOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
    } catch (Exception e) {
      throw new RuntimeException("Failed to delete dataproc cluster", e);
    }

    // Call dangling resource cleanup
    danglingResourceCleanupService.cleanupResourcesSuppressExceptions();

    // Verify that the cluster is deleted, polling to ensure dangling resource cleanup flight to
    // delete the cluster has completed
    boolean isDeleted = false;
    for (int retryCount = 0; retryCount < ASSERT_RESOURCE_CLEANUP_RETRY_COUNT; retryCount++) {
      try {
        resourceDao.getResource(workspace.getWorkspaceId(), cluster.getMetadata().getResourceId());
      } catch (ResourceNotFoundException e) {
        isDeleted = true;
        break;
      }
      TimeUnit.SECONDS.sleep(ASSERT_RESOURCE_CLEANUP_RETRY_SECONDS);
    }
    assertTrue(isDeleted, "Cluster is deleted");

    // Verify that the instance is not deleted
    assertNotNull(
        resourceDao.getResource(
            workspace.getWorkspaceId(), instance.getMetadata().getResourceId()));

    // Verify that the bucket is not deleted
    assertNotNull(
        resourceDao.getResource(workspace.getWorkspaceId(), bucket.getMetadata().getResourceId()));
  }
}
