package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.MockGcpApi.CONTROLLED_GCP_GCS_BUCKETS_PATH_FORMAT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.FolderDao;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.ApiCloneResourceResult;
import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.JobResultOrException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.AwaitCloneAllResourcesFlightStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.AwaitCreateCloudContextFlightStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.CloneAllFoldersStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.FindResourcesToCloneStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.LaunchCloneAllResourcesFlightStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.workspace.LaunchCreateCloudContextFlightStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
  public static final String SPEND_PROFILE_ID = "wm-default-spend-profile";
  private static final Logger logger = LoggerFactory.getLogger(GcpCloudContextConnectedTest.class);
  // Name of the test WSM application. This must match the identifier in the
  // application-app-test.yml file.
  private static final String TEST_WSM_APP = "TestWsmApp";
  private static final String FOLDER_NAME = "FolderName";
  private static final UUID FOLDER_ID = UUID.randomUUID();

  @MockBean private DataRepoService mockDataRepoService;

  @Autowired private MockMvc mockMvc;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private FolderDao folderDao;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private JobService jobService;
  @Autowired private MockMvcUtils mockMvcUtils;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ReferencedResourceService referenceResourceService;
  @Autowired private ResourceDao resourceDao;
  @Autowired private SamService samService;
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
        mockMvcUtils.createWorkspaceWithCloudContext(userRequest, apiCloudPlatform).getId();
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
            mockMvcUtils.deleteWorkspaceNoCheck(
                userAccessUtils.defaultUserAuthRequest(), workspaceId);
        assertTrue(
            status == HttpStatus.NO_CONTENT.value() || status == HttpStatus.NOT_FOUND.value());
        workspaceId = null;
      }
      if (workspaceId2 != null) {
        int status =
            mockMvcUtils.deleteWorkspaceNoCheck(
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
    mockMvcUtils.deleteGcpCloudContext(userRequest, workspaceId);

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

    mockMvcUtils.deleteWorkspace(userRequest, workspaceId);
    workspaceId = null;

    // Check that project is now being deleted.
    workspaceConnectedTestUtils.assertProjectIsBeingDeleted(projectId);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGetDeleteGoogleContext_deleteGcpProjectAndLog() throws Exception {
    assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceId).isPresent());

    mockMvcUtils.deleteGcpCloudContext(userAccessUtils.defaultUserAuthRequest(), workspaceId);

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
        mockMvcUtils
            .createWorkspaceWithoutCloudContext(
                userAccessUtils.defaultUserAuthRequest(), ApiWorkspaceStageModel.MC_WORKSPACE)
            .getId();
    mockMvcUtils.createCloudContextAndWait(
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
    return mockMvcUtils
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
            /*description=*/ null,
            /*parentFolderId=*/ null,
            /*properties=*/ Map.of(),
            "foo@gmail.com",
            null);
    folderDao.createFolder(sourceFolder);

    workspaceId2 = UUID.randomUUID();
    Workspace destinationWorkspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceId2)
            .userFacingId("dest-user-facing-id")
            .displayName("Destination Workspace")
            .description("Copied from source")
            .spendProfileId(new SpendProfileId(SPEND_PROFILE_ID))
            .build();

    String destinationLocation = "us-east1";

    String cloneJobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace,
            userAccessUtils.defaultUserAuthRequest(),
            destinationLocation,
            /*additionalPolicies=*/ null,
            destinationWorkspace,
            spendUtils.defaultGcpSpendProfile());
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

  @Test
  @Disabled("PF-2259 - this test needs rewriting")
  // TODO(PF-2259): This test is not testing the undo of CloneGcpWorkspaceFlight. It is
  //  testing the undo of WorkspaceCreateFlight. It needs to be rewritten.
  public void cloneGcpWorkspaceUndoSteps() {
    Workspace sourceWorkspace = workspaceService.getWorkspace(workspaceId);
    // Enable an application
    appService.enableWorkspaceApplication(
        userAccessUtils.defaultUserAuthRequest(), sourceWorkspace, TEST_WSM_APP);

    // Create a folder
    folderDao.createFolder(
        new Folder(
            FOLDER_ID,
            workspaceId,
            FOLDER_NAME,
            /*description=*/ null,
            /*parentFolderId=*/ null,
            /*properties=*/ Map.of(),
            "foo@gmail.com",
            null));

    // Create a referenced resource
    ReferencedBigQueryDatasetResource datasetReference =
        referenceResourceService
            .createReferenceResource(
                ReferenceResourceFixtures.makeReferencedBqDatasetResource(
                    sourceWorkspace.getWorkspaceId(), "my-project", "fake_dataset"),
                userAccessUtils.defaultUserAuthRequest())
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
    ApiGcpBigQueryDatasetCreationParameters creationParameters =
        new ApiGcpBigQueryDatasetCreationParameters()
            .datasetId("my_awesome_dataset")
            .location("us-central1");
    ControlledBigQueryDatasetResource resource =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(
                sourceWorkspace.getWorkspaceId())
            .projectId(projectId)
            .datasetName("my_awesome_dataset")
            .build();

    // Create a controlled resource
    ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, null, userAccessUtils.defaultUserAuthRequest(), creationParameters)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    workspaceId2 = UUID.randomUUID();
    Workspace destinationWorkspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceId2)
            .userFacingId("dest-user-facing-id")
            .displayName("Destination Workspace")
            .description("Copied from source")
            .spendProfileId(new SpendProfileId(SPEND_PROFILE_ID))
            .build();

    String destinationLocation = "us-east1";
    // Retry undo steps once and fail at the end of the flight.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CloneAllFoldersStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(FindResourcesToCloneStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        LaunchCreateCloudContextFlightStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        AwaitCreateCloudContextFlightStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        LaunchCloneAllResourcesFlightStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(
        AwaitCloneAllResourcesFlightStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    // TODO(PF-2259): Since the test is actually setting debug for the create workspace flight
    //  lastStepFailure(true) will always cause a dismal failure.
    FlightDebugInfo debugInfo =
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build();

    jobService.setFlightDebugInfoForTest(debugInfo);

    assertThrows(
        InvalidResultStateException.class,
        () ->
            workspaceService.cloneWorkspace(
                sourceWorkspace,
                userAccessUtils.defaultUserAuthRequest(),
                destinationLocation,
                /*additionalPolicies=*/ null,
                destinationWorkspace,
                spendUtils.defaultGcpSpendProfile()));
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(destinationWorkspace.getWorkspaceId()));

    // Destination Workspace should not have a GCP context
    assertTrue(
        gcpCloudContextService.getGcpCloudContext(destinationWorkspace.getWorkspaceId()).isEmpty());

    // Destination workspace should not have folder
    assertTrue(folderDao.listFoldersInWorkspace(destinationWorkspace.getWorkspaceId()).isEmpty());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            resourceDao.getResource(
                destinationWorkspace.getWorkspaceId(), createdDataset.getResourceId()));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            resourceDao.getResource(
                destinationWorkspace.getWorkspaceId(), datasetReference.getResourceId()));
  }
}
