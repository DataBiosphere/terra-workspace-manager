package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID;
import static bio.terra.workspace.common.mocks.MockGcpApi.CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.mocks.MockGcpApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.generated.model.ApiCloneResourceResult;
import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.JobResultOrException;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Use application configuration profile in addition to the standard connected test profile
// inherited from the base class.
@Tag("connectedPlus")
@ActiveProfiles({"app-test"})
class GcpCloudContextConnectedTest extends BaseConnectedTest {
  private static final Logger logger = LoggerFactory.getLogger(GcpCloudContextConnectedTest.class);
  // Name of the test WSM application. This must match the identifier in the
  // application-app-test.yml file.
  private static final String TEST_WSM_APP = "TestWsmApp";
  private static final String FOLDER_NAME = "FolderName";
  private static final UUID FOLDER_ID = UUID.randomUUID();

  @MockBean private DataRepoService mockDataRepoService;

  @Autowired private MockMvc mockMvc;
  @Autowired private FolderDao folderDao;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired private MockGcpApi mockGcpApi;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceActivityLogService workspaceActivityLogService;
  @Autowired private WsmApplicationService appService;
  @Autowired private WorkspaceConnectedTestUtils workspaceConnectedTestUtils;

  private UUID workspaceId;
  private String projectId;
  private UUID workspaceId2;

  @BeforeEach
  void setup() throws Exception {
    jobService.setFlightDebugInfoForTest(null);
    doReturn(true).when(mockDataRepoService).snapshotReadable(any(), any(), any());
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();
    workspaceId =
        mockWorkspaceV1Api.createWorkspaceWithCloudContext(userRequest, apiCloudPlatform).getId();
    projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
    workspaceId2 = null;
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    // Reset the debug info before trying to delete. Otherwise, the delete flight can have
    // unexpected retry failures.
    jobService.setFlightDebugInfoForTest(null);
    try {
      if (workspaceId != null) {
        int status =
            mockWorkspaceV1Api.deleteWorkspaceIfExists(
                userAccessUtils.defaultUserAuthRequest(), workspaceId);
        assertTrue(
            status == HttpStatus.NO_CONTENT.value() || status == HttpStatus.NOT_FOUND.value());
        workspaceId = null;
      }
      if (workspaceId2 != null) {
        int status =
            mockWorkspaceV1Api.deleteWorkspaceIfExists(
                userAccessUtils.defaultUserAuthRequest(), workspaceId2);
        assertTrue(
            status == HttpStatus.NO_CONTENT.value() || status == HttpStatus.NOT_FOUND.value());
        workspaceId2 = null;
      }
    } catch (Exception ex) {
      logger.warn("Failed to delete workspaces after test");
    }
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteWorkspaceWithGoogleContext() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    // Reach in and find the project id
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);

    // Verify project exists
    workspaceConnectedTestUtils.assertProjectExist(projectId);

    // Create a controlled resource
    ApiGcpGcsBucketResource bucketResource = createControlledBucket();

    // Delete the cloud context
    mockWorkspaceV1Api.deleteCloudContext(userRequest, workspaceId, CloudPlatform.GCP);

    // Make sure the bucket gets deleted when we delete the cloud context
    String errorResponseString =
        mockMvc
            .perform(
                MockMvcUtils.addAuth(
                    get(
                        CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT.formatted(
                            workspaceId, bucketResource.getMetadata().getResourceId())),
                    userRequest))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiErrorReport errorReport = objectMapper.readValue(errorResponseString, ApiErrorReport.class);
    assertEquals(HttpStatus.NOT_FOUND.value(), errorReport.getStatusCode());

    mockWorkspaceV1Api.deleteWorkspace(userRequest, workspaceId);
    workspaceId = null;

    // Check that project is now being deleted.
    workspaceConnectedTestUtils.assertProjectIsBeingDeleted(projectId);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGetDeleteGoogleContext_deleteGcpProjectAndLog() throws Exception {
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceId).isPresent());

    mockWorkspaceV1Api.deleteCloudContext(
        userAccessUtils.defaultUserAuthRequest(), workspaceId, CloudPlatform.GCP);

    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceId).isEmpty());
    ActivityLogChangeDetails changeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceId).get();
    assertEquals(OperationType.DELETE, changeDetails.operationType());
    assertEquals(workspaceId.toString(), changeDetails.changeSubjectId());
    assertEquals(ActivityLogChangedTarget.GCP_CLOUD_CONTEXT, changeDetails.changeSubjectType());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGoogleContext_logCreation() throws Exception {
    workspaceId2 =
        mockWorkspaceV1Api
            .createWorkspaceWithoutCloudContext(
                userAccessUtils.defaultUserAuthRequest(), ApiWorkspaceStageModel.MC_WORKSPACE)
            .getId();
    mockWorkspaceV1Api.createCloudContextAndWait(
        userAccessUtils.defaultUserAuthRequest(), workspaceId2, apiCloudPlatform);

    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceId2).isPresent());
    ActivityLogChangeDetails changeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceId2).get();
    assertEquals(OperationType.CREATE, changeDetails.operationType());
    assertEquals(workspaceId2.toString(), changeDetails.changeSubjectId());
    assertEquals(ActivityLogChangedTarget.GCP_CLOUD_CONTEXT, changeDetails.changeSubjectType());
  }

  private ApiGcpGcsBucketResource createControlledBucket() throws Exception {
    // Add a bucket resource
    String bucketName = "terra-test-" + UUID.randomUUID().toString().toLowerCase();
    return mockGcpApi
        .createControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            "bucket_1", /* resource name */
            bucketName,
            "us-west4",
            ApiGcpGcsBucketDefaultStorageClass.STANDARD,
            null) /* lifecycle */
        .getGcpBucket();
  }

  @Test
  public void cloneGcpWorkspace() throws Exception {
    // Add a bucket resource
    ApiGcpGcsBucketResource bucketResource = createControlledBucket();

    // Enable an application
    Workspace sourceWorkspace = workspaceService.getWorkspace(workspaceId);
    appService.enableWorkspaceApplication(
        userAccessUtils.defaultUserAuthRequest(), sourceWorkspace, TEST_WSM_APP);

    // Create a folder
    Folder sourceFolder =
        new Folder(
            FOLDER_ID,
            workspaceId,
            FOLDER_NAME,
            /* description= */ null,
            /* parentFolderId= */ null,
            /* properties= */ Map.of(),
            "foo@gmail.com",
            null);
    folderDao.createFolder(sourceFolder);

    workspaceId2 = UUID.randomUUID();
    Workspace destinationWorkspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceId2)
            .userFacingId("dest-user-facing-id")
            .displayName("Destination Workspace")
            .description("Copied from source")
            .spendProfileId(DEFAULT_GCP_SPEND_PROFILE_ID)
            .build();

    String destinationLocation = "us-east1";

    String cloneJobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace,
            userAccessUtils.defaultUserAuthRequest(),
            destinationLocation,
            /* additionalPolicies= */ null,
            destinationWorkspace,
            spendUtils.defaultGcpSpendProfile(),
            /* projectOwnerGroupId= */ null);
    jobService.waitForJob(cloneJobId);
    JobResultOrException<ApiClonedWorkspace> cloneResultOrException =
        jobService.retrieveJobResult(cloneJobId, ApiClonedWorkspace.class);
    assertNull(cloneResultOrException.getException());
    ApiClonedWorkspace cloneResult = cloneResultOrException.getResult();
    assertEquals(destinationWorkspace.getWorkspaceId(), cloneResult.getDestinationWorkspaceId());
    assertThat(cloneResult.getResources(), hasSize(1));

    ApiResourceCloneDetails bucketCloneDetails = cloneResult.getResources().get(0);
    assertEquals(ApiCloneResourceResult.SUCCEEDED, bucketCloneDetails.getResult());
    assertNull(bucketCloneDetails.getErrorMessage());
    assertEquals(ApiResourceType.GCS_BUCKET, bucketCloneDetails.getResourceType());
    assertEquals(
        bucketResource.getMetadata().getResourceId(), bucketCloneDetails.getSourceResourceId());

    // destination WS should exist
    Workspace retrievedDestinationWorkspace =
        workspaceService.getWorkspace(destinationWorkspace.getWorkspaceId());
    assertEquals(
        "Destination Workspace", retrievedDestinationWorkspace.getDisplayName().orElseThrow());
    assertEquals(
        "Copied from source", retrievedDestinationWorkspace.getDescription().orElseThrow());
    assertEquals(WorkspaceStage.MC_WORKSPACE, retrievedDestinationWorkspace.getWorkspaceStage());

    // Destination Workspace should have a GCP context
    assertNotNull(
        gcpCloudContextService
            .getGcpCloudContext(destinationWorkspace.getWorkspaceId())
            .orElseThrow());

    // Destination workspace should have an enabled application
    assertTrue(appService.getWorkspaceApplication(destinationWorkspace, TEST_WSM_APP).isEnabled());

    // Destination workspace should have 1 cloned folder with the relations
    assertThat(folderDao.listFoldersInWorkspace(destinationWorkspace.getWorkspaceId()), hasSize(1));
    assertFalse(
        folderDao
            .listFoldersInWorkspace(destinationWorkspace.getWorkspaceId())
            .contains(sourceFolder));
  }
}
