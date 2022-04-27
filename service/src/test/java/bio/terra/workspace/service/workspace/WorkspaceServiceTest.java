package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.common.sam.exception.SamExceptionFactory;
import bio.terra.common.sam.exception.SamInternalServerErrorException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.DuplicateUserFacingIdException;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.exceptions.StageDisabledException;
import bio.terra.workspace.service.workspace.flight.CheckSamWorkspaceAuthzStep;
import bio.terra.workspace.service.workspace.flight.CreateWorkspaceAuthzStep;
import bio.terra.workspace.service.workspace.flight.CreateWorkspaceStep;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.junit.jupiter.api.AfterEach;
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
  @Autowired private WorkspaceConnectedTestUtils testUtils;
  @MockBean private DataRepoService dataRepoService;
  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

  @BeforeEach
  void setup() throws Exception {
    doReturn(true).when(dataRepoService).snapshotReadable(any(), any(), any());
    // By default, allow all spend link calls as authorized. (All other isAuthorized calls return
    // false by Mockito default.
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamResource.SPEND_PROFILE),
                Mockito.any(),
                Mockito.eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    // Return a valid google group for cloud sync, as Google validates groups added to GCP projects.
    Mockito.when(mockSamService.syncWorkspacePolicy(any(), any(), any()))
        .thenReturn("terra-workspace-manager-test-group@googlegroups.com");
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
  }

  @Test
  void testGetMissingWorkspace() {
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  void testGetExistingWorkspace() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    assertEquals(
        request.getWorkspaceId(),
        workspaceService.getWorkspace(request.getWorkspaceId(), USER_REQUEST).getWorkspaceId());
  }

  @Test
  void testGetForbiddenMissingWorkspace() throws Exception {
    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  void testGetForbiddenExistingWorkspace() throws Exception {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());
    assertThrows(
        ForbiddenException.class,
        () -> workspaceService.getWorkspace(request.getWorkspaceId(), USER_REQUEST));
  }

  @Test
  void testWorkspaceStagePersists() {
    Workspace mcWorkspaceRequest =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(mcWorkspaceRequest, USER_REQUEST);
    Workspace createdWorkspace =
        workspaceService.getWorkspace(mcWorkspaceRequest.getWorkspaceId(), USER_REQUEST);
    assertEquals(mcWorkspaceRequest.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(WorkspaceStage.MC_WORKSPACE, createdWorkspace.getWorkspaceStage());
  }

  @Test
  void duplicateWorkspaceIdRequestsRejected() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    Workspace duplicateWorkspace =
        defaultRequestBuilder(request.getWorkspaceId())
            .description("slightly different workspace")
            .build();
    assertThrows(
        DuplicateWorkspaceException.class,
        () -> workspaceService.createWorkspace(duplicateWorkspace, USER_REQUEST));
  }

  @Test
  void duplicateWorkspaceUserFacingIdRequestsRejected() {
    String userFacingId = "create-workspace-user-facing-id";
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    Workspace duplicateUserFacingId =
            defaultRequestBuilder(UUID.randomUUID()).userFacingId(userFacingId)
                    .build();

    DuplicateUserFacingIdException ex = assertThrows(
            DuplicateUserFacingIdException.class,
            () -> workspaceService.createWorkspace(duplicateUserFacingId, USER_REQUEST));
    assertEquals(ex.getMessage(), String.format("Workspace with ID %s already exists", userFacingId));
  }

  @Test
  void duplicateOperationSharesFailureResponse() throws Exception {
    String errorMsg = "fake SAM error message";
    doThrow(SamExceptionFactory.create(errorMsg, new ApiException(("test"))))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    assertThrows(
        ErrorReportException.class,
        () ->
            workspaceService.createWorkspace(
                defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
    // This second call shares the above operation ID, and so should return the same exception
    // instead of a more generic internal Stairway exception.
    assertThrows(
        ErrorReportException.class,
        () ->
            workspaceService.createWorkspace(
                defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
  }

  @Test
  void testWithSpendProfile() {
    SpendProfileId spendProfileId = new SpendProfileId("foo");
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID()).spendProfileId(spendProfileId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    Workspace createdWorkspace =
        workspaceService.getWorkspace(request.getWorkspaceId(), USER_REQUEST);
    assertEquals(request.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals(spendProfileId, createdWorkspace.getSpendProfileId().orElse(null));
  }

  @Test
  void testWithDisplayNameAndDescription() {
    String name = "My workspace";
    String description = "The greatest workspace";
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID()).displayName(name).description(description).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    Workspace createdWorkspace =
        workspaceService.getWorkspace(request.getWorkspaceId(), USER_REQUEST);
    assertEquals(
        request.getDescription().orElse(null), createdWorkspace.getDescription().orElse(null));
    assertEquals(name, createdWorkspace.getDisplayName().orElse(null));
    assertEquals(description, createdWorkspace.getDescription().orElse(null));
  }

  @Test
  void testUpdateWorkspace() {
    Map<String, String> propertyMap = new HashMap<>();
    propertyMap.put("foo", "bar");
    propertyMap.put("xyzzy", "plohg");
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).properties(propertyMap).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    Workspace createdWorkspace =
        workspaceService.getWorkspace(request.getWorkspaceId(), USER_REQUEST);
    assertEquals(request.getWorkspaceId(), createdWorkspace.getWorkspaceId());
    assertEquals("", createdWorkspace.getDisplayName().orElse(null));
    assertEquals("", createdWorkspace.getDescription().orElse(null));

    UUID workspaceId = request.getWorkspaceId();
    String userFacingId = "my-user-facing-id";
    String name = "My workspace";
    String description = "The greatest workspace";
    Map<String, String> propertyMap2 = new HashMap<>();
    propertyMap.put("ted", "lasso");
    propertyMap.put("keeley", "jones");

    Workspace updatedWorkspace =
        workspaceService.updateWorkspace(
            USER_REQUEST, workspaceId, userFacingId, name, description, propertyMap2);

    assertEquals(name, updatedWorkspace.getDisplayName().orElse(null));
    assertEquals(description, updatedWorkspace.getDescription().orElse(null));
    assertEquals(propertyMap2, updatedWorkspace.getProperties());

    String otherDescription = "The deprecated workspace";

    Workspace secondUpdatedWorkspace =
        workspaceService.updateWorkspace(USER_REQUEST, workspaceId, null, null, otherDescription, null);

    // Since name is null, leave it alone. Description should be updated.
    assertEquals(userFacingId, secondUpdatedWorkspace.getUserFacingId().orElse(null));
    assertEquals(name, secondUpdatedWorkspace.getDisplayName().orElse(null));
    assertEquals(otherDescription, secondUpdatedWorkspace.getDescription().orElse(null));
    assertEquals(propertyMap2, updatedWorkspace.getProperties());

    // Sending through empty strings and an empty map clears the values.
    Map<String, String> propertyMap3 = new HashMap<>();
    Workspace thirdUpdatedWorkspace =
        workspaceService.updateWorkspace(USER_REQUEST, workspaceId, userFacingId, "", "", propertyMap3);
    assertEquals("", thirdUpdatedWorkspace.getDisplayName().orElse(null));
    assertEquals("", thirdUpdatedWorkspace.getDescription().orElse(null));

    assertThrows(
        MissingRequiredFieldException.class,
        () -> workspaceService.updateWorkspace(USER_REQUEST, workspaceId, null, null, null, null));
  }

  @Test
  void testUpdateWorkspaceUserFacingIdAlreadyExistsRejected() {
    // Create one workspace with userFacingId, one without.
    String userFacingId = "update-workspace-user-facing-id";
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).userFacingId(userFacingId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    UUID secondWorkspaceUuid = UUID.randomUUID();
    request = defaultRequestBuilder(secondWorkspaceUuid).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    // Try to set second workspace's userFacing to first.
    DuplicateUserFacingIdException ex = assertThrows(
            DuplicateUserFacingIdException.class,
            () ->  workspaceService.updateWorkspace(
                    USER_REQUEST, secondWorkspaceUuid, userFacingId, null, null, null));
    assertEquals(ex.getMessage(), String.format("Workspace with ID %s already exists", userFacingId));
  }

  @Test
  void testHandlesSamError() throws Exception {
    String apiErrorMsg = "test";
    ErrorReportException testex = new SamInternalServerErrorException(apiErrorMsg);
    doThrow(testex).when(mockSamService).createWorkspaceWithDefaults(any(), any());
    ErrorReportException exception =
        assertThrows(
            SamInternalServerErrorException.class,
            () ->
                workspaceService.createWorkspace(
                    defaultRequestBuilder(UUID.randomUUID()).build(), USER_REQUEST));
    assertEquals(apiErrorMsg, exception.getMessage());
  }

  @Test
  void createAndDeleteWorkspace() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    workspaceService.deleteWorkspace(request.getWorkspaceId(), USER_REQUEST);
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(request.getWorkspaceId(), USER_REQUEST));
  }

  @Test
  void createMcWorkspaceDoSteps() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    UUID createdId = workspaceService.createWorkspace(request, USER_REQUEST);
    assertEquals(createdId, request.getWorkspaceId());
  }

  @Test
  void createRawlsWorkspaceDoSteps() throws InterruptedException {
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    // Ensure the auth check in CheckSamWorkspaceAuthzStep always succeeds.
    doReturn(true).when(mockSamService).isAuthorized(any(), any(), any(), any());
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(
        CheckSamWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    UUID createdId = workspaceService.createWorkspace(request, USER_REQUEST);
    assertEquals(createdId, request.getWorkspaceId());
  }

  @Test
  void createMcWorkspaceUndoSteps() {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    // Retry undo steps once and fail at the end of the flight.
    Map<String, StepStatus> retrySteps = new HashMap<>();
    retrySteps.put(CreateWorkspaceAuthzStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    retrySteps.put(CreateWorkspaceStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
    FlightDebugInfo debugInfo =
        FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build();
    jobService.setFlightDebugInfoForTest(debugInfo);

    // Service methods which wait for a flight to complete will throw an
    // InvalidResultStateException when that flight fails without a cause, which occurs when a
    // flight fails via debugInfo.
    assertThrows(
        InvalidResultStateException.class,
        () -> workspaceService.createWorkspace(request, USER_REQUEST));
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(request.getWorkspaceId(), USER_REQUEST));
  }

  @Test
  void deleteForbiddenMissingWorkspace() throws Exception {
    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());

    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.deleteWorkspace(UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  void deleteForbiddenExistingWorkspace() throws Exception {
    Workspace request = defaultRequestBuilder(UUID.randomUUID()).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    doThrow(new ForbiddenException("forbid!"))
        .when(mockSamService)
        .checkAuthz(any(), any(), any(), any());

    assertThrows(
        ForbiddenException.class,
        () -> workspaceService.deleteWorkspace(request.getWorkspaceId(), USER_REQUEST));
  }

  @Test
  void deleteWorkspaceWithDataReference() {
    // First, create a workspace.
    UUID workspaceId = UUID.randomUUID();
    Workspace request = defaultRequestBuilder(workspaceId).build();
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
    workspaceService.deleteWorkspace(request.getWorkspaceId(), USER_REQUEST);

    // Verify that the workspace was successfully deleted, even though it contained references
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(workspaceId, USER_REQUEST));

    // Verify that the resource is also deleted
    assertThrows(
        ResourceNotFoundException.class, () -> resourceDao.getResource(workspaceId, resourceId));
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void deleteWorkspaceWithGoogleContext() throws Exception {
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(spendUtils.defaultSpendId())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(
        request.getWorkspaceId(), jobId, USER_REQUEST, "/fake/value");
    jobService.waitForJob(jobId);
    assertNull(jobService.retrieveJobResult(jobId, Object.class, USER_REQUEST).getException());
    Workspace workspace = workspaceService.getWorkspace(request.getWorkspaceId(), USER_REQUEST);
    String projectId =
        workspaceService.getAuthorizedRequiredGcpProject(workspace.getWorkspaceId(), USER_REQUEST);

    // Verify project exists by retrieving it.
    crl.getCloudResourceManagerCow().projects().get(projectId).execute();

    workspaceService.deleteWorkspace(request.getWorkspaceId(), USER_REQUEST);

    // Check that project is now being deleted.
    Project project = crl.getCloudResourceManagerCow().projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getState());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void createGetDeleteGoogleContext() {
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(spendUtils.defaultSpendId())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    String jobId = UUID.randomUUID().toString();
    workspaceService.createGcpCloudContext(
        request.getWorkspaceId(), jobId, USER_REQUEST, "/fake/value");
    jobService.waitForJob(jobId);
    assertNull(jobService.retrieveJobResult(jobId, Object.class, USER_REQUEST).getException());
    assertTrue(
        testUtils.getAuthorizedGcpCloudContext(request.getWorkspaceId(), USER_REQUEST).isPresent());
    workspaceService.deleteGcpCloudContext(request.getWorkspaceId(), USER_REQUEST);
    assertTrue(
        testUtils.getAuthorizedGcpCloudContext(request.getWorkspaceId(), USER_REQUEST).isEmpty());
  }

  @Test
  void createGoogleContextRawlsStageThrows() throws Exception {
    // RAWLS_WORKSPACE stage workspaces use existing Sam resources instead of owning them, so the
    // mock pretends our user has access to any workspace we ask about.
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamResource.WORKSPACE),
                Mockito.any(),
                Mockito.eq(SamWorkspaceAction.READ)))
        .thenReturn(true);
    Workspace request =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);
    String jobId = UUID.randomUUID().toString();

    assertThrows(
        StageDisabledException.class,
        () ->
            workspaceService.createGcpCloudContext(
                request.getWorkspaceId(), jobId, USER_REQUEST, "/fake/value"));
  }

  /**
   * Convenience method for getting a WorkspaceRequest builder with some pre-filled default values.
   *
   * <p>This provides default values for jobId (random UUID), spend profile (Optional.empty()), and
   * workspace stage (MC_WORKSPACE).
   *
   * <p>Because the tests in this class mock Sam, we do not need to explicitly clean up workspaces
   * created here.
   */
  private Workspace.Builder defaultRequestBuilder(UUID workspaceId) {
    return Workspace.builder()
        .workspaceId(workspaceId)
        .spendProfileId(null)
        .workspaceStage(WorkspaceStage.MC_WORKSPACE);
  }
}
