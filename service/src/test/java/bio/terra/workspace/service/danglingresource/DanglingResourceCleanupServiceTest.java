package bio.terra.workspace.service.danglingresource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.workspace.app.configuration.external.DanglingResourceCleanupConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.services.notebooks.v1.model.Operation;
import java.time.Duration;
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
  private final int ASSERT_RESOURCE_CLEANUP_RETRY_COUNT = 8;
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

  /** Set up default workspace */
  @BeforeEach
  public void setup() {
    // The cleanup cronjob will not run because it's initially disabled via configuration, but for
    // these tests we can enable it and manually trigger cleanup. No gap between runs to ensure
    // tests don't interfere with each other.
    danglingResourceCleanupConfiguration.setEnabled(true);
    danglingResourceCleanupConfiguration.setPollingInterval(Duration.ZERO);
    workspace =
        workspaceUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());
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

    // Create a controlled AI notebook instance
    ApiGcpAiNotebookInstanceResource notebook =
        mockMvcUtils
            .createAiNotebookInstance(
                userAccessUtils.defaultUserAuthRequest(), workspace.getWorkspaceId(), null)
            .getAiNotebookInstance();

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

    // Directly delete the notebook
    AIPlatformNotebooksCow notebooksCow = crlService.getAIPlatformNotebooksCow();
    InstanceName instanceName =
        InstanceName.builder()
            .projectId(notebook.getAttributes().getProjectId())
            .location(notebook.getAttributes().getLocation())
            .instanceId(notebook.getAttributes().getInstanceId())
            .build();
    try {
      OperationCow<Operation> deleteOperation =
          notebooksCow
              .operations()
              .operationCow(notebooksCow.instances().delete(instanceName).execute());
      GcpUtils.pollAndRetry(deleteOperation, Duration.ofSeconds(20), Duration.ofMinutes(12));
    } catch (Exception e) {
      throw new RuntimeException("Failed to delete notebook instance", e);
    }

    // Call dangling resource cleanup
    danglingResourceCleanupService.cleanupResourcesSuppressExceptions();

    // Verify that the notebook is deleted, polling to ensure dangling resource cleanup flight to
    // delete the notebook has completed
    boolean isDeleted = false;
    for (int retryCount = 0; retryCount < ASSERT_RESOURCE_CLEANUP_RETRY_COUNT; retryCount++) {
      try {
        resourceDao.getResource(workspace.getWorkspaceId(), notebook.getMetadata().getResourceId());
      } catch (ResourceNotFoundException e) {
        isDeleted = true;
        break;
      }
      TimeUnit.SECONDS.sleep(ASSERT_RESOURCE_CLEANUP_RETRY_SECONDS);
    }
    assertTrue(isDeleted, "Notebook was not deleted");

    // Verify that the instance is not deleted
    assertNotNull(
        resourceDao.getResource(
            workspace.getWorkspaceId(), instance.getMetadata().getResourceId()));

    // Verify that the bucket is not deleted
    assertNotNull(
        resourceDao.getResource(workspace.getWorkspaceId(), bucket.getMetadata().getResourceId()));
  }
}
