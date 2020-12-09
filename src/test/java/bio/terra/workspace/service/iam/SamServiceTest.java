package bio.terra.workspace.service.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.model.IamRole;
import bio.terra.workspace.service.iam.model.RoleBinding;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.StageDisabledException;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class SamServiceTest extends BaseConnectedTest {

  @Autowired private SamService samService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private DataReferenceService dataReferenceService;

  @MockBean private DataRepoService mockDataRepoService;

  @BeforeEach
  public void setup() {
    doReturn(true).when(mockDataRepoService).snapshotExists(any(), any(), any());
  }

  @Test
  public void AddedReaderCanRead() {
    UUID workspaceId = createWorkspaceDefaultUser();
    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        SamUnauthorizedException.class,
        () -> workspaceService.getWorkspace(workspaceId, secondaryUserRequest()));
    // After being granted permission, secondary user can read the workspace.
    samService.addWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.READER, userAccessUtils.secondUserEmail());
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceId, secondaryUserRequest());
    assertEquals(workspaceId, readWorkspace.workspaceId());
  }

  @Test
  public void AddedWriterCanWrite() {
    UUID workspaceId = createWorkspaceDefaultUser();
    DataReferenceRequest referenceRequest =
        DataReferenceRequest.builder()
            .workspaceId(workspaceId)
            .name("valid_name")
            .referenceType(DataReferenceType.DATA_REPO_SNAPSHOT)
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            // We mock TDR in this test so all snapshots are considered valid.
            .referenceObject(SnapshotReference.create("fakeInstance", "fakeSnapshot"))
            .build();
    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        SamUnauthorizedException.class,
        () -> dataReferenceService.createDataReference(referenceRequest, secondaryUserRequest()));
    // After being granted permission, secondary user can modify the workspace.
    samService.addWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.WRITER, userAccessUtils.secondUserEmail());
    DataReference reference =
        dataReferenceService.createDataReference(referenceRequest, secondaryUserRequest());
    assertEquals(referenceRequest.name(), reference.name());
  }

  @Test
  public void RemovedReaderCannotRead() {
    UUID workspaceId = createWorkspaceDefaultUser();
    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        SamUnauthorizedException.class,
        () -> workspaceService.getWorkspace(workspaceId, secondaryUserRequest()));
    // After being granted permission, secondary user can read the workspace.
    samService.addWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.READER, userAccessUtils.secondUserEmail());
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceId, secondaryUserRequest());
    assertEquals(workspaceId, readWorkspace.workspaceId());
    // After removing permission, secondary user can no longer read.
    samService.removeWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.READER, userAccessUtils.secondUserEmail());
    assertThrows(
        SamUnauthorizedException.class,
        () -> workspaceService.getWorkspace(workspaceId, secondaryUserRequest()));
  }

  @Test
  public void NonOwnerCannotAddReader() {
    UUID workspaceId = createWorkspaceDefaultUser();
    // Note that this request uses the secondary user's authentication token, when only the first
    // user is an owner.
    assertThrows(
        SamUnauthorizedException.class,
        () ->
            samService.addWorkspaceRole(
                workspaceId,
                defaultUserRequest(),
                IamRole.READER,
                userAccessUtils.secondUserEmail()));
  }

  @Test
  public void PermissionsApiFailsInRawlsWorkspace() {
    WorkspaceRequest rawlsRequest =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .jobId(UUID.randomUUID().toString())
            .build();
    UUID workspaceId = workspaceService.createWorkspace(rawlsRequest, defaultUserRequest());
    assertThrows(
        StageDisabledException.class,
        () ->
            samService.addWorkspaceRole(
                workspaceId,
                defaultUserRequest(),
                IamRole.READER,
                userAccessUtils.secondUserEmail()));
  }

  @Test
  public void InvalidUserEmailRejected() {
    UUID workspaceId = createWorkspaceDefaultUser();
    assertThrows(
        SamApiException.class,
        () ->
            samService.addWorkspaceRole(
                workspaceId, defaultUserRequest(), IamRole.READER, "!!!INVALID EMAIL ADDRESS!!!!"));
  }

  @Test
  public void ListPermissionsIncludesAddedUsers() {
    UUID workspaceId = createWorkspaceDefaultUser();
    samService.addWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.READER, userAccessUtils.secondUserEmail());
    List<RoleBinding> policyList = samService.listRoleBindings(workspaceId, defaultUserRequest());

    RoleBinding expectedOwnerBinding =
        RoleBinding.builder()
            .role(IamRole.OWNER)
            .users(Collections.singletonList(userAccessUtils.defaultUserEmail()))
            .build();
    RoleBinding expectedReaderBinding =
        RoleBinding.builder()
            .role(IamRole.READER)
            .users(Collections.singletonList(userAccessUtils.secondUserEmail()))
            .build();
    assertThat(
        policyList,
        containsInAnyOrder(equalTo(expectedOwnerBinding), equalTo(expectedReaderBinding)));
  }

  @Test
  public void WriterCannotListPermissions() {
    UUID workspaceId = createWorkspaceDefaultUser();
    samService.addWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.WRITER, userAccessUtils.secondUserEmail());
    assertThrows(
        SamApiException.class,
        () -> samService.listRoleBindings(workspaceId, secondaryUserRequest()));
  }

  /**
   * Convenience wrapper to build an AuthenticatedUserRequest from utils' default user.
   *
   * <p>This only fills in access token, not email or subjectId..
   */
  private AuthenticatedUserRequest defaultUserRequest() {
    return new AuthenticatedUserRequest(
        null, null, Optional.of(userAccessUtils.defaultUserAccessToken().getTokenValue()));
  }

  /**
   * Convenience wrapper to build an AuthenticatedUserRequest from utils' secondary default user.
   *
   * <p>This only fills in access token, not email or subjectId.
   */
  private AuthenticatedUserRequest secondaryUserRequest() {
    return new AuthenticatedUserRequest(
        null, null, Optional.of(userAccessUtils.secondUserAccessToken().getTokenValue()));
  }

  /** Create a workspace using the default test user for connected tests, return its ID.. */
  private UUID createWorkspaceDefaultUser() {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .jobId(UUID.randomUUID().toString())
            .build();
    return workspaceService.createWorkspace(request, defaultUserRequest());
  }
}
