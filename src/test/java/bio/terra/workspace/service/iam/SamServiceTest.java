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
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datareference.model.WsmResourceType;
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

class SamServiceTest extends BaseConnectedTest {

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
  void AddedReaderCanRead() {
    UUID workspaceId = createWorkspaceDefaultUser();
    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        SamUnauthorizedException.class,
        () -> workspaceService.getWorkspace(workspaceId, secondaryUserRequest()));
    // After being granted permission, secondary user can read the workspace.
    samService.grantWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.READER, userAccessUtils.getSecondUserEmail());
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceId, secondaryUserRequest());
    assertEquals(workspaceId, readWorkspace.getWorkspaceId());
  }

  @Test
  void AddedWriterCanWrite() {
    UUID workspaceId = createWorkspaceDefaultUser();
    DataReferenceRequest referenceRequest =
        DataReferenceRequest.builder()
            .workspaceId(workspaceId)
            .name("valid_name")
            .referenceType(WsmResourceType.DATA_REPO_SNAPSHOT)
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            // We mock TDR in this test so all snapshots are considered valid.
            .referenceObject(SnapshotReference.create("fakeInstance", "fakeSnapshot"))
            .build();
    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        SamUnauthorizedException.class,
        () -> dataReferenceService.createDataReference(referenceRequest, secondaryUserRequest()));
    // After being granted permission, secondary user can modify the workspace.
    samService.grantWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.WRITER, userAccessUtils.getSecondUserEmail());
    DataReference reference =
        dataReferenceService.createDataReference(referenceRequest, secondaryUserRequest());
    assertEquals(referenceRequest.name(), reference.name());
  }

  @Test
  void RemovedReaderCannotRead() {
    UUID workspaceId = createWorkspaceDefaultUser();
    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        SamUnauthorizedException.class,
        () -> workspaceService.getWorkspace(workspaceId, secondaryUserRequest()));
    // After being granted permission, secondary user can read the workspace.
    samService.grantWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.READER, userAccessUtils.getSecondUserEmail());
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceId, secondaryUserRequest());
    assertEquals(workspaceId, readWorkspace.getWorkspaceId());
    // After removing permission, secondary user can no longer read.
    samService.removeWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.READER, userAccessUtils.getSecondUserEmail());
    assertThrows(
        SamUnauthorizedException.class,
        () -> workspaceService.getWorkspace(workspaceId, secondaryUserRequest()));
  }

  @Test
  void NonOwnerCannotAddReader() {
    UUID workspaceId = createWorkspaceDefaultUser();
    // Note that this request uses the secondary user's authentication token, when only the first
    // user is an owner.
    assertThrows(
        SamUnauthorizedException.class,
        () ->
            samService.grantWorkspaceRole(
                workspaceId,
                secondaryUserRequest(),
                IamRole.READER,
                userAccessUtils.getSecondUserEmail()));
  }

  @Test
  void PermissionsApiFailsInRawlsWorkspace() {
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
            samService.grantWorkspaceRole(
                workspaceId,
                defaultUserRequest(),
                IamRole.READER,
                userAccessUtils.getSecondUserEmail()));
  }

  @Test
  void InvalidUserEmailRejected() {
    UUID workspaceId = createWorkspaceDefaultUser();
    assertThrows(
        SamApiException.class,
        () ->
            samService.grantWorkspaceRole(
                workspaceId, defaultUserRequest(), IamRole.READER, "!!!INVALID EMAIL ADDRESS!!!!"));
  }

  @Test
  void ListPermissionsIncludesAddedUsers() {
    UUID workspaceId = createWorkspaceDefaultUser();
    samService.grantWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.READER, userAccessUtils.getSecondUserEmail());
    List<RoleBinding> policyList = samService.listRoleBindings(workspaceId, defaultUserRequest());

    RoleBinding expectedOwnerBinding =
        RoleBinding.builder()
            .role(IamRole.OWNER)
            .users(Collections.singletonList(userAccessUtils.getDefaultUserEmail()))
            .build();
    RoleBinding expectedReaderBinding =
        RoleBinding.builder()
            .role(IamRole.READER)
            .users(Collections.singletonList(userAccessUtils.getSecondUserEmail()))
            .build();
    RoleBinding expectedWriterBinding =
        RoleBinding.builder().role(IamRole.WRITER).users(Collections.emptyList()).build();
    assertThat(
        policyList,
        containsInAnyOrder(
            equalTo(expectedOwnerBinding),
            equalTo(expectedWriterBinding),
            equalTo(expectedReaderBinding)));
  }

  @Test
  void WriterCannotListPermissions() {
    UUID workspaceId = createWorkspaceDefaultUser();
    samService.grantWorkspaceRole(
        workspaceId, defaultUserRequest(), IamRole.WRITER, userAccessUtils.getSecondUserEmail());
    assertThrows(
        SamUnauthorizedException.class,
        () -> samService.listRoleBindings(workspaceId, secondaryUserRequest()));
  }

  @Test
  void GrantRoleInMissingWorkspaceThrows() {
    UUID fakeId = UUID.randomUUID();
    assertThrows(
        WorkspaceNotFoundException.class,
        () ->
            samService.grantWorkspaceRole(
                fakeId,
                defaultUserRequest(),
                IamRole.READER,
                userAccessUtils.getSecondUserEmail()));
  }

  @Test
  void ReadRolesInMissingWorkspaceThrows() {
    UUID fakeId = UUID.randomUUID();
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> samService.listRoleBindings(fakeId, defaultUserRequest()));
  }

  /**
   * Convenience method to build an AuthenticatedUserRequest from utils' default user.
   *
   * <p>This only fills in access token, not email or subjectId.
   */
  private AuthenticatedUserRequest defaultUserRequest() {
    return new AuthenticatedUserRequest(
        null, null, Optional.of(userAccessUtils.defaultUserAccessToken().getTokenValue()));
  }

  /**
   * Convenience method to build an AuthenticatedUserRequest from utils' secondary default user.
   *
   * <p>This only fills in access token, not email or subjectId.
   */
  private AuthenticatedUserRequest secondaryUserRequest() {
    return new AuthenticatedUserRequest(
        null, null, Optional.of(userAccessUtils.secondUserAccessToken().getTokenValue()));
  }

  /** Create a workspace using the default test user for connected tests, return its ID. */
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
