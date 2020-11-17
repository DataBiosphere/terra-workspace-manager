package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.exception.*;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.generated.model.CloningInstructionsEnum;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.exceptions.SpendUnauthorizedException;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.api.services.cloudresourcemanager.model.Project;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;

public class WorkspaceServiceTest extends BaseConnectedTest {
  @Autowired private WorkspaceService workspaceService;
  @Autowired private DataReferenceService dataReferenceService;
  @Autowired private JobService jobService;
  @Autowired private CloudResourceManagerCow resourceManager;
  @Autowired private SpendConnectedTestUtils spendUtils;

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
  public void setup() {
    doReturn(true).when(dataRepoService).snapshotExists(any(), any(), any());
    // By default, allow all spend link calls as authorized. (All other isAuthorized calls return
    // false by Mockito default.
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamUtils.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamUtils.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(true);
  }

  @Test
  public void testGetMissingWorkspace() {
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  public void testGetExistingWorkspace() {
    WorkspaceRequest request = defaultWorkspaceRequest();
    workspaceService.createWorkspace(request, USER_REQUEST);

    assertEquals(
        request.workspaceId(),
        workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST).workspaceId());
  }

  @Test
  public void testWorkspaceStagePersists() {
    WorkspaceRequest mcWorkspaceRequest =
        defaultRequestBuilder(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(mcWorkspaceRequest, USER_REQUEST);
    Workspace createdWorkspace =
        workspaceService.getWorkspace(mcWorkspaceRequest.workspaceId(), USER_REQUEST);
    assertEquals(mcWorkspaceRequest.workspaceId(), createdWorkspace.workspaceId());
    assertEquals(WorkspaceStage.MC_WORKSPACE, createdWorkspace.workspaceStage());
  }

  @Test
  public void duplicateWorkspaceIdRequestsRejected() {
    WorkspaceRequest request = defaultWorkspaceRequest();
    workspaceService.createWorkspace(request, USER_REQUEST);
    // Note that the two calls use different operationIDs for the same Workspace ID, making them
    // logically distinct requests to create the same workspace.
    WorkspaceRequest duplicateWorkspace = defaultRequestBuilder(request.workspaceId()).build();
    assertThrows(
        DuplicateWorkspaceException.class,
        () -> workspaceService.createWorkspace(duplicateWorkspace, USER_REQUEST));
  }

  @Test
  public void duplicateOperationRequestOk() {
    WorkspaceRequest request = defaultWorkspaceRequest();
    UUID returnedId = workspaceService.createWorkspace(request, USER_REQUEST);
    // Because these calls share the same operationID they're treated as duplicate requests, rather
    // than separate attempts to create the same workspace.
    UUID duplicateReturnedId = workspaceService.createWorkspace(request, USER_REQUEST);
    assertEquals(returnedId, duplicateReturnedId);
    assertEquals(returnedId, request.workspaceId());
  }

  @Test
  public void duplicateOperationSharesFailureResponse() {
    String errorMsg = "fake SAM error message";
    doThrow(new SamApiException(errorMsg))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    assertThrows(
        SamApiException.class,
        () -> workspaceService.createWorkspace(defaultWorkspaceRequest(), USER_REQUEST));
    // This second call shares the above operation ID, and so should return the same SamApiException
    // instead of a more generic internal Stairway exception.
    assertThrows(
        SamApiException.class,
        () -> workspaceService.createWorkspace(defaultWorkspaceRequest(), USER_REQUEST));
  }

  @Test
  public void testWithSpendProfile() {
    Optional<SpendProfileId> spendProfileId = Optional.of(SpendProfileId.create("foo"));
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID()).spendProfileId(spendProfileId).build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    Workspace createdWorkspace = workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST);
    assertEquals(request.workspaceId(), createdWorkspace.workspaceId());
    assertEquals(spendProfileId, createdWorkspace.spendProfileId());
  }

  @Test
  public void testHandlesSamError() {
    String errorMsg = "fake SAM error message";

    doThrow(new SamApiException(errorMsg))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    SamApiException exception =
        assertThrows(
            SamApiException.class,
            () -> workspaceService.createWorkspace(defaultWorkspaceRequest(), USER_REQUEST));
    assertEquals(errorMsg, exception.getMessage());
  }

  @Test
  public void createAndDeleteWorkspace() {
    WorkspaceRequest request = defaultWorkspaceRequest();
    workspaceService.createWorkspace(request, USER_REQUEST);

    workspaceService.deleteWorkspace(request.workspaceId(), USER_REQUEST);
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(request.workspaceId(), USER_REQUEST));
  }

  @Test
  public void deleteWorkspaceWithDataReference() {
    // First, create a workspace.
    WorkspaceRequest request = defaultWorkspaceRequest();
    workspaceService.createWorkspace(request, USER_REQUEST);

    // Next, add a data reference to that workspace.
    DataRepoSnapshot reference =
        new DataRepoSnapshot().instanceName("fake instance").snapshot("fake snapshot");
    CreateDataReferenceRequestBody referenceRequest =
        new CreateDataReferenceRequestBody()
            .name("fake_data_reference")
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(reference);
    UUID referenceId =
        dataReferenceService
            .createDataReference(request.workspaceId(), referenceRequest, USER_REQUEST)
            .getReferenceId();
    // Validate that the reference exists.
    dataReferenceService.getDataReference(request.workspaceId(), referenceId, USER_REQUEST);
    // Delete the workspace.
    workspaceService.deleteWorkspace(request.workspaceId(), USER_REQUEST);
    // Verify that the contained data reference is no longer returned.
    assertThrows(
        DataReferenceNotFoundException.class,
        () ->
            dataReferenceService.getDataReference(
                request.workspaceId(), referenceId, USER_REQUEST));
  }

  @Test
  public void deleteWorkspaceWithGoogleContext() throws Exception {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    String jobId = workspaceService.createGoogleContext(request.workspaceId(), USER_REQUEST);
    jobService.waitForJob(jobId);
    assertEquals(
        HttpStatus.OK,
        jobService.retrieveJobResult(jobId, Object.class, USER_REQUEST).getStatusCode());
    String projectId =
        workspaceService
            .getCloudContext(request.workspaceId(), USER_REQUEST)
            .googleProjectId()
            .get();
    // Verify project exists by retrieving it.
    Project project = resourceManager.projects().get(projectId).execute();

    workspaceService.deleteWorkspace(request.workspaceId(), USER_REQUEST);
    // Check that project is now being deleted.
    project = resourceManager.projects().get(projectId).execute();
    assertEquals("DELETE_REQUESTED", project.getLifecycleState());
  }

  @Test
  public void createGetDeleteGoogleContext() {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    String jobId = workspaceService.createGoogleContext(request.workspaceId(), USER_REQUEST);
    jobService.waitForJob(jobId);
    assertEquals(
        HttpStatus.OK,
        jobService.retrieveJobResult(jobId, Object.class, USER_REQUEST).getStatusCode());
    assertTrue(
        workspaceService
            .getCloudContext(request.workspaceId(), USER_REQUEST)
            .googleProjectId()
            .isPresent());

    workspaceService.deleteGoogleContext(request.workspaceId(), USER_REQUEST);
    assertEquals(
        WorkspaceCloudContext.none(),
        workspaceService.getCloudContext(request.workspaceId(), USER_REQUEST));
  }

  @Test
  public void createGoogleContextNoSpendProfileIdThrows() {
    WorkspaceRequest request = defaultWorkspaceRequest();
    workspaceService.createWorkspace(request, USER_REQUEST);

    assertThrows(
        MissingSpendProfileException.class,
        () -> workspaceService.createGoogleContext(request.workspaceId(), USER_REQUEST));
  }

  @Test
  public void createGoogleContextSpendLinkingUnauthorizedThrows() {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(Optional.of(spendUtils.defaultSpendId()))
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.eq(USER_REQUEST.getRequiredToken()),
                Mockito.eq(SamUtils.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamUtils.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(false);
    assertThrows(
        SpendUnauthorizedException.class,
        () -> workspaceService.createGoogleContext(request.workspaceId(), USER_REQUEST));
  }

  @Test
  public void createGoogleContextSpendWithoutBillingAccountThrows() {
    WorkspaceRequest request =
        defaultRequestBuilder(UUID.randomUUID())
            .spendProfileId(Optional.of(spendUtils.noBillingAccount()))
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);

    assertThrows(
        NoBillingAccountException.class,
        () -> workspaceService.createGoogleContext(request.workspaceId(), USER_REQUEST));
  }

  /**
   * Convenience method for getting a WorkspaceRequest builder with some pre-filled default values.
   *
   * <p>This provides default values for operationID (random UUID), spend profile
   * (Optional.empty()), and workspace stage (RAWLS_WORKSPACE).
   */
  private WorkspaceRequest.Builder defaultRequestBuilder(UUID workspaceId) {
    return WorkspaceRequest.builder()
        .workspaceId(workspaceId)
        .jobId(UUID.randomUUID().toString())
        .spendProfileId(Optional.empty())
        .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE);
  }

  /**
   * Generate a fully built Workspace object with a random ID and default values in all fields.
   *
   * <p>See {@code defaultRequestBuilder} for default values of each field.
   */
  private WorkspaceRequest defaultWorkspaceRequest() {
    return defaultRequestBuilder(UUID.randomUUID()).build();
  }
}
