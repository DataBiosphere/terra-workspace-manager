package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.DuplicateJobIdException;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.exceptions.StageDisabledException;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.api.services.cloudresourcemanager.model.Project;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class WorkspaceServiceTest extends BaseConnectedTest {
  @Autowired private WorkspaceService workspaceService;
  @Autowired private DataReferenceService dataReferenceService;
  @Autowired private JobService jobService;
  @Autowired private CrlService crl;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private DataReferenceDao dataReferenceDao;

  @MockBean private DataRepoService dataRepoService;

  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

  /** A fake authenticated user request. */
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest()
          .token(Optional.of("fake-token"))
          .email("fake@email.com")
          .subjectId("fakeID123");

  @BeforeEach
  void setup() {
    doReturn(true).when(dataRepoService).snapshotExists(any(), any(), any());
    // By default, allow all spend link calls as authorized. (All other isAuthorized calls return
    // false by Mockito default.
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(true);
    // Return a valid google group for cloud sync, as Google validates groups added to GCP projects.
    Mockito.when(mockSamService.syncWorkspacePolicy(any(), any(), any()))
        .thenReturn("terra-workspace-manager-test-group@googlegroups.com");
  }

  @Test
  void testGetMissingWorkspace() {
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  void testGetExistingWorkspace() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    assertEquals(
        request.workspaceId(),
        workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST).getWorkspaceId());
  }

  @Test
  void testGetForbiddenMissingWorkspace() {
    doThrow(new SamUnauthorizedException("forbid!"))
        .when(mockSamService)
        .workspaceAuthzOnly(any(), any(), any());
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  void testGetForbiddenExistingWorkspace() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    doThrow(new SamUnauthorizedException("forbid!"))
        .when(mockSamService)
        .workspaceAuthzOnly(any(), any(), any());
    assertThrows(
        SamUnauthorizedException.class,
        () -> workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST));
  }

  @Test
  void testWorkspaceStagePersists() {
    WorkspaceRequest mcWorkspaceRequest =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(mcWorkspaceRequest, USER_REQUEST);
    Workspace createdWorkspace =
        workspaceService.getWorkspace(mcWorkspaceRequest.workspaceId(), USER_REQUEST);
    assertEquals(mcWorkspaceRequest.workspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(WorkspaceStage.MC_WORKSPACE, createdWorkspace.getWorkspaceStage());
  }

  @Test
  void duplicateWorkspaceIdRequestsRejected() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    // Note that the two calls use different jobIds for the same Workspace ID, making them
    // logically distinct requests to create the same workspace.
    WorkspaceRequest duplicateWorkspace = defaultRequestBuilder(request.workspaceId()).build();
    assertThrows(
        DuplicateWorkspaceException.class,
        () -> workspaceService.createWorkspace(duplicateWorkspace, USER_REQUEST));
  }

  @Test
  void duplicateJobIdRequestRejected() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    // Because these calls share the same jobId they're treated as duplicate requests, rather
    // than separate attempts to create the same workspace.
    assertThrows(
        DuplicateJobIdException.class,
        () -> workspaceService.createWorkspace(request, USER_REQUEST));
  }

  @Test
  void duplicateOperationSharesFailureResponse() {
    String errorMsg = "fake SAM error message";
    doThrow(new SamApiException(errorMsg))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    assertThrows(
        SamApiException.class,
        () ->
            workspaceService.createWorkspace(
                defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
    // This second call shares the above operation ID, and so should return the same SamApiException
    // instead of a more generic internal Stairway exception.
    assertThrows(
        SamApiException.class,
        () ->
            workspaceService.createWorkspace(
                defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
  }

  @Test
  void testWithSpendProfile() {
    Optional<SpendProfileId> spendProfileId = Optional.of(SpendProfileId.create("foo"));
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID()).spendProfileId(spendProfileId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    Workspace createdWorkspace = workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST);
    assertEquals(request.workspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(spendProfileId, createdWorkspace.getSpendProfileId());
  }

  @Test
  void testHandlesSamError() {
    String errorMsg = "fake SAM error message";

    doThrow(new SamApiException(errorMsg))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    SamApiException exception =
        assertThrows(
            SamApiException.class,
            () ->
                workspaceService.createWorkspace(
                    defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
    assertEquals(errorMsg, exception.getMessage());
  }

  @Test
  void createAndDeleteWorkspace() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    workspaceService.deleteWorkspace(request.workspaceId(), USER_REQUEST);
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST));
  }

  @Test
  void deleteForbiddenMissingWorkspace() {
    doThrow(new SamUnauthorizedException("forbid!"))
        .when(mockSamService)
        .workspaceAuthzOnly(any(), any(), any());

    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.deleteWorkspace(UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  void deleteForbiddenExistingWorkspace() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    doThrow(new SamUnauthorizedException("forbid!"))
        .when(mockSamService)
        .workspaceAuthzOnly(any(), any(), any());

    assertThrows(
        SamUnauthorizedException.class,
        () -> workspaceService.deleteWorkspace(request.workspaceId(), USER_REQUEST));
  }

  @Test
  void deleteWorkspaceWithDataReference() {
    // First, create a workspace.
    UUID workspaceId = UUID.randomUUID();
    WorkspaceRequest request = defaultRequestBuilder(workspaceId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    // Next, add a data reference to that workspace.
    SnapshotReference snapshot = SnapshotReference.create("fake instance", "fake snapshot");
    DataReferenceRequest referenceRequest =
        DataReferenceRequest.builder()
            .workspaceId(workspaceId)
            .name("fake_data_reference")
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            .referenceType(DataReferenceType.DATA_REPO_SNAPSHOT)
            .referenceObject(snapshot)
            .build();

    UUID referenceId =
        dataReferenceService.createDataReference(referenceRequest, USER_REQUEST).referenceId();
    // Validate that the reference exists.
    dataReferenceService.getDataReference(request.workspaceId(), referenceId, USER_REQUEST);
    // Delete the workspace.
    workspaceService.deleteWorkspace(request.workspaceId(), USER_REQUEST);
    // Verify that the workspace was successfully deleted, even though it contained references

    // Verify the data ref rows in question were also deleted; this is a direct call to the SQL
    // table
    assertThrows(
        DataReferenceNotFoundException.class,
        () -> dataReferenceDao.getDataReference(request.workspaceId(), referenceId));

    // Verify that attempting to retrieve the reference via DataReferenceService fails; this
    // includes
    // workspace-existence and permission checks
    assertThrows(
        WorkspaceNotFoundException.class,
        () ->
            dataReferenceService.getDataReference(
                request.workspaceId(), referenceId, USER_REQUEST));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = bufferServiceDisabledEnvsRegEx)
  void deleteWorkspaceWithGoogleContext() throws Exception {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(
        request.workspaceId(), jobId, "/fake/value", USER_REQUEST);
    jobService.waitForJob(jobId);
    assertNull(jobService.retrieveJobResult(jobId, Object.class, USER_REQUEST).getException());
    Workspace workspace = workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST);
    String projectId =
        workspace.getGcpCloudContext().map(GcpCloudContext::getGcpProjectId).orElse(null);
    assertNotNull(projectId);

    // Verify project exists by retrieving it.
    crl.getCloudResourceManagerCow().projects().get(projectId).execute();

    workspaceService.deleteWorkspace(request.workspaceId(), USER_REQUEST);

    // Check that project is now being deleted.
    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = bufferServiceDisabledEnvsRegEx)
  void createGetDeleteGoogleContext() {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    String jobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(
        request.workspaceId(), jobId, "/fake/value", USER_REQUEST);
    jobService.waitForJob(jobId);
    assertNull(jobService.retrieveJobResult(jobId, Object.class, USER_REQUEST).getException());
    Workspace workspace = workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST);
    assertTrue(workspace.getGcpCloudContext().isPresent());

    workspaceService.deleteGcpCloudContext(request.workspaceId(), USER_REQUEST);
    workspace = workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST);
    assertTrue(workspace.getGcpCloudContext().isEmpty());
  }

  @Test
  void createGoogleContextRawlsStageThrows() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();

    assertThrows(
        StageDisabledException.class,
        () ->
            workspaceService.createGcpCloudContext(
                request.workspaceId(), jobId, "/fake/value", USER_REQUEST));
  }

  @Test
  void createGoogleContextNoSpendProfileIdThrows() {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();

    assertThrows(
        MissingSpendProfileException.class,
        () ->
            workspaceService.createGcpCloudContext(
                request.workspaceId(), jobId, "/fake/value", USER_REQUEST));
  }

  @Test
  void createGoogleContextSpendLinkingUnauthorizedThrows() {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();

    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.eq(USER_REQUEST.getRequiredToken()),
                Mockito.eq(SamConstants.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(false);
    assertThrows(
        SpendUnauthorizedException.class,
        () ->
            workspaceService.createGcpCloudContext(
                request.workspaceId(), jobId, "/fake/value", USER_REQUEST));
  }

  @Test
  void createGoogleContextSpendWithoutBillingAccountThrows() {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(Optional.of(spendUtils.noBillingAccount()))
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();

    assertThrows(
        NoBillingAccountException.class,
        () ->
            workspaceService.createGcpCloudContext(
                request.workspaceId(), jobId, "/fake/value", USER_REQUEST));
  }

  /**
   * Convenience method for getting a WorkspaceRequest builder with some pre-filled default values.
   *
   * <p>This provides default values for jobId (random UUID), spend profile (Optional.empty()), and
   * workspace stage (RAWLS_WORKSPACE).
   */
  private WorkspaceRequest.Builder defaultRequestBuilder(UUID workspaceId) {
    return WorkspaceRequest.builder()
        .workspaceId(workspaceId)
        .jobId(UUID.randomUUID().toString())
        .spendProfileId(Optional.empty())
        .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE);
  }
}
