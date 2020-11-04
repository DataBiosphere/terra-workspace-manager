package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.exception.*;
import bio.terra.workspace.common.model.WorkspaceStage;
import bio.terra.workspace.generated.model.CloningInstructionsEnum;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.generated.model.WorkspaceStageModel;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;

public class WorkspaceServiceTest extends BaseConnectedTest {
  @Autowired private WorkspaceService workspaceService;
  @Autowired private DataReferenceService dataReferenceService;
  @Autowired private JobService jobService;

  @MockBean private DataRepoService dataRepoService;

  /** Mock SamService returns true for all calls to {@link SamService#isAuthorized}. */
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
  }

  @Test
  public void testGetMissingWorkspace() {
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  public void testGetExistingWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody().id(workspaceId);

    CreatedWorkspace workspace =
        workspaceService.createWorkspace(body, WorkspaceStage.RAWLS_WORKSPACE, USER_REQUEST);
    assertEquals(workspaceId, workspace.getId());

    assertEquals(workspaceId, workspaceService.getWorkspace(workspaceId, USER_REQUEST).getId());
  }

  @Test
  public void testWorkspaceStagePersists() {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody().id(workspaceId).stage(WorkspaceStageModel.MC_WORKSPACE);

    CreatedWorkspace workspace =
        workspaceService.createWorkspace(body, WorkspaceStage.MC_WORKSPACE, USER_REQUEST);
    assertEquals(workspaceId, workspace.getId());
    WorkspaceDescription description = workspaceService.getWorkspace(workspaceId, USER_REQUEST);
    assertEquals(workspaceId, description.getId());
    assertEquals(WorkspaceStageModel.MC_WORKSPACE, description.getStage());
  }

  @Test
  public void duplicateWorkspaceRejected() {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody().id(workspaceId).spendProfile(null).policies(null);
    CreatedWorkspace workspace =
        workspaceService.createWorkspace(body, WorkspaceStage.RAWLS_WORKSPACE, USER_REQUEST);
    assertEquals(workspaceId, workspace.getId());

    assertThrows(
        DuplicateWorkspaceException.class,
        () -> workspaceService.createWorkspace(body, WorkspaceStage.RAWLS_WORKSPACE, USER_REQUEST));
  }

  @Test
  public void testWithSpendProfileAndPolicies() {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .spendProfile(UUID.randomUUID())
            .policies(Collections.singletonList(UUID.randomUUID()));

    CreatedWorkspace workspace =
        workspaceService.createWorkspace(body, WorkspaceStage.RAWLS_WORKSPACE, USER_REQUEST);
    assertEquals(workspaceId, workspace.getId());
  }

  @Test
  public void testHandlesSamError() {
    String errorMsg = "fake SAM error message";

    doThrow(new SamApiException(errorMsg))
        .when(mockSamService)
        .createWorkspaceWithDefaults(any(), any());

    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody().id(UUID.randomUUID());
    SamApiException exception =
        assertThrows(
            SamApiException.class,
            () ->
                workspaceService.createWorkspace(
                    body, WorkspaceStage.RAWLS_WORKSPACE, USER_REQUEST));
    assertEquals(errorMsg, exception.getMessage());
  }

  @Test
  public void createAndDeleteWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody().id(workspaceId);

    CreatedWorkspace workspace =
        workspaceService.createWorkspace(body, WorkspaceStage.RAWLS_WORKSPACE, USER_REQUEST);
    assertEquals(workspaceId, workspace.getId());

    workspaceService.deleteWorkspace(workspaceId, USER_REQUEST);
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceService.getWorkspace(workspaceId, USER_REQUEST));
  }

  @Test
  public void deleteWorkspaceWithDataReference() {
    // First, create a workspace.
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody body = new CreateWorkspaceRequestBody().id(workspaceId);
    CreatedWorkspace workspace =
        workspaceService.createWorkspace(body, WorkspaceStage.RAWLS_WORKSPACE, USER_REQUEST);
    assertEquals(workspaceId, workspace.getId());

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
            .createDataReference(workspaceId, referenceRequest, USER_REQUEST)
            .getReferenceId();
    // Validate that the reference exists.
    dataReferenceService.getDataReference(workspaceId, referenceId, USER_REQUEST);
    // Delete the workspace.
    workspaceService.deleteWorkspace(workspaceId, USER_REQUEST);
    // Verify that the contained data reference is no longer returned.
    assertThrows(
        DataReferenceNotFoundException.class,
        () -> dataReferenceService.getDataReference(workspaceId, referenceId, USER_REQUEST));
  }

  @Test
  public void createAndGetGoogleContext() {
    UUID workspaceId = UUID.randomUUID();
    workspaceService.createWorkspace(
        new CreateWorkspaceRequestBody().id(workspaceId),
        WorkspaceStage.RAWLS_WORKSPACE,
        USER_REQUEST);

    String jobId = workspaceService.createGoogleContext(workspaceId, USER_REQUEST);
    jobService.waitForJob(jobId);
    assertEquals(
        HttpStatus.OK,
        jobService.retrieveJobResult(jobId, Object.class, USER_REQUEST).getStatusCode());

    assertTrue(
        workspaceService.getCloudContext(workspaceId, USER_REQUEST).googleProjectId().isPresent());
  }
}
