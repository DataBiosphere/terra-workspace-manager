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
import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.DuplicateJobIdException;
import bio.terra.workspace.service.job.exception.InvalidJobIdException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
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
  /** A fake authenticated user request. */
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest()
          .token(Optional.of("fake-token"))
          .email("fake@email.com")
          .subjectId("fakeID123");

  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private CrlService crl;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private ReferencedResourceService referenceResourceService;
  @Autowired private ResourceDao resourceDao;
  @MockBean private DataRepoService dataRepoService;
  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

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
  void emptyJobIdRequestRejected() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).jobId("").build();

    assertEquals("", request.jobId());
    // create-workspace request specifies the empty string for jobId
    assertThrows(
        InvalidJobIdException.class, () -> workspaceService.createWorkspace(request, USER_REQUEST));
  }

  @Test
  void whitespaceJobIdRequestRejected() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).jobId("  \t  ").build();
    // create-workspace request specifies a whitespace-only string for jobId
    assertThrows(
        InvalidJobIdException.class, () -> workspaceService.createWorkspace(request, USER_REQUEST));
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
  void testWithDisplayNameAndDescription() {
    String name = "My workspace";
    String description = "The greatest workspace";
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .displayName(Optional.of(name))
            .description(Optional.of(description))
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    Workspace createdWorkspace = workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST);
    assertEquals(request.workspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(name, createdWorkspace.getDisplayName().get());
    assertEquals(description, createdWorkspace.getDescription().get());
  }

  @Test
  void testUpdateWorkspace() {
    WorkspaceRequest request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    Workspace createdWorkspace = workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST);
    assertEquals(request.workspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals("", createdWorkspace.getDisplayName().get());
    assertEquals("", createdWorkspace.getDescription().get());

    UUID workspaceId = request.workspaceId();
    String name = "My workspace";
    String description = "The greatest workspace";

    Workspace updatedWorkspace =
        workspaceService.updateWorkspace(USER_REQUEST, workspaceId, name, description);

    assertEquals(name, updatedWorkspace.getDisplayName().get());
    assertEquals(description, updatedWorkspace.getDescription().get());

    String otherDescription = "The deprecated workspace";

    Workspace secondUpdatedWorkspace =
        workspaceService.updateWorkspace(USER_REQUEST, workspaceId, null, otherDescription);

    // Since name is null, leave it alone. Description should be updated.
    assertEquals(name, secondUpdatedWorkspace.getDisplayName().get());
    assertEquals(otherDescription, secondUpdatedWorkspace.getDescription().get());

    // Sending through empty strings clears the values.
    Workspace thirdUpdatedWorkspace =
        workspaceService.updateWorkspace(USER_REQUEST, workspaceId, "", "");
    assertEquals("", thirdUpdatedWorkspace.getDisplayName().get());
    assertEquals("", thirdUpdatedWorkspace.getDescription().get());

    assertThrows(
        MissingRequiredFieldException.class,
        () -> workspaceService.updateWorkspace(USER_REQUEST, workspaceId, null, null));
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
    UUID resourceId = UUID.randomUUID();
    ReferencedDataRepoSnapshotResource snapshot =
        new ReferencedDataRepoSnapshotResource(
            workspaceId,
            resourceId,
            "fake_data_reference",
            null,
            CloningInstructions.COPY_NOTHING,
            "fakeinstance",
            "fakesnapshot");
    referenceResourceService.createReferenceResource(snapshot, USER_REQUEST);

    // Validate that the reference exists.
    referenceResourceService.getReferenceResource(workspaceId, resourceId, USER_REQUEST);

    // Delete the workspace.
    workspaceService.deleteWorkspace(request.workspaceId(), USER_REQUEST);

    // Verify that the workspace was successfully deleted, even though it contained references
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(workspaceId, USER_REQUEST));

    // Verify that the resource is also deleted
    assertThrows(
        ResourceNotFoundException.class, () -> resourceDao.getResource(workspaceId, resourceId));
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
    // RAWLS_WORKSPACE stage workspaces use existing Sam resources instead of owning them, so the
    // mock pretends our user has access to any workspace we ask about.
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamConstants.SAM_WORKSPACE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamConstants.SAM_WORKSPACE_READ_ACTION)))
        .thenReturn(true);
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
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
   * workspace stage (MC_WORKSPACE).
   */
  private WorkspaceRequest.Builder defaultRequestBuilder(UUID workspaceId) {
    return WorkspaceRequest.builder()
        .workspaceId(workspaceId)
        .jobId(UUID.randomUUID().toString())
        .spendProfileId(Optional.empty())
        .workspaceStage(WorkspaceStage.MC_WORKSPACE);
  }
}
