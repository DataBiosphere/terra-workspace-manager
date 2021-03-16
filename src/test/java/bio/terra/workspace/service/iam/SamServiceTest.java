package bio.terra.workspace.service.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.model.RoleBinding;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
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
  @Autowired private ReferencedResourceService referenceResourceService;

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
        workspaceId, defaultUserRequest(), WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceId, secondaryUserRequest());
    assertEquals(workspaceId, readWorkspace.getWorkspaceId());
  }

  @Test
  void AddedWriterCanWrite() {
    UUID workspaceId = createWorkspaceDefaultUser();

    ReferencedDataRepoSnapshotResource referenceResource =
        ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);

    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        SamUnauthorizedException.class,
        () ->
            referenceResourceService.createReferenceResource(
                referenceResource, secondaryUserRequest()));

    // After being granted permission, secondary user can modify the workspace.
    samService.grantWorkspaceRole(
        workspaceId, defaultUserRequest(), WsmIamRole.WRITER, userAccessUtils.getSecondUserEmail());

    ReferencedResource ref =
        referenceResourceService.createReferenceResource(referenceResource, secondaryUserRequest());
    ReferencedDataRepoSnapshotResource resultResource = ref.castToDataRepoSnapshotResource();
    assertEquals(referenceResource, resultResource);
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
        workspaceId, defaultUserRequest(), WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceId, secondaryUserRequest());
    assertEquals(workspaceId, readWorkspace.getWorkspaceId());
    // After removing permission, secondary user can no longer read.
    samService.removeWorkspaceRole(
        workspaceId, defaultUserRequest(), WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
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
                WsmIamRole.READER,
                userAccessUtils.getSecondUserEmail()));
  }

  @Test
  void PermissionsApiFailsInRawlsWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    // RAWLS_WORKSPACEs do not own their own Sam resources, so we need to manage them separately.
    samService.createWorkspaceWithDefaults(defaultUserRequest(), workspaceId);

    WorkspaceRequest rawlsRequest =
        WorkspaceRequest.builder()
            .workspaceId(workspaceId)
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .jobId(UUID.randomUUID().toString())
            .build();
    workspaceService.createWorkspace(rawlsRequest, defaultUserRequest());
    assertThrows(
        StageDisabledException.class,
        () ->
            samService.grantWorkspaceRole(
                workspaceId,
                defaultUserRequest(),
                WsmIamRole.READER,
                userAccessUtils.getSecondUserEmail()));

    samService.deleteWorkspace(defaultUserRequest().getRequiredToken(), workspaceId);
  }

  @Test
  void InvalidUserEmailRejected() {
    UUID workspaceId = createWorkspaceDefaultUser();
    assertThrows(
        SamApiException.class,
        () ->
            samService.grantWorkspaceRole(
                workspaceId,
                defaultUserRequest(),
                WsmIamRole.READER,
                "!!!INVALID EMAIL ADDRESS!!!!"));
  }

  @Test
  void ListPermissionsIncludesAddedUsers() {
    UUID workspaceId = createWorkspaceDefaultUser();
    samService.grantWorkspaceRole(
        workspaceId, defaultUserRequest(), WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    List<RoleBinding> policyList = samService.listRoleBindings(workspaceId, defaultUserRequest());

    RoleBinding expectedOwnerBinding =
        RoleBinding.builder()
            .role(WsmIamRole.OWNER)
            .users(Collections.singletonList(userAccessUtils.getDefaultUserEmail()))
            .build();
    RoleBinding expectedReaderBinding =
        RoleBinding.builder()
            .role(WsmIamRole.READER)
            .users(Collections.singletonList(userAccessUtils.getSecondUserEmail()))
            .build();
    RoleBinding expectedWriterBinding =
        RoleBinding.builder().role(WsmIamRole.WRITER).users(Collections.emptyList()).build();
    RoleBinding expectedApplicationBinding =
        RoleBinding.builder().role(WsmIamRole.APPLICATION).users(Collections.emptyList()).build();
    assertThat(
        policyList,
        containsInAnyOrder(
            equalTo(expectedOwnerBinding),
            equalTo(expectedWriterBinding),
            equalTo(expectedReaderBinding),
            equalTo(expectedApplicationBinding)));
  }

  @Test
  void WriterCannotListPermissions() {
    UUID workspaceId = createWorkspaceDefaultUser();
    samService.grantWorkspaceRole(
        workspaceId, defaultUserRequest(), WsmIamRole.WRITER, userAccessUtils.getSecondUserEmail());
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
                WsmIamRole.READER,
                userAccessUtils.getSecondUserEmail()));
  }

  @Test
  void ReadRolesInMissingWorkspaceThrows() {
    UUID fakeId = UUID.randomUUID();
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> samService.listRoleBindings(fakeId, defaultUserRequest()));
  }

  @Test
  void ListWorkspacesIncludesWsmWorkspace() {
    // This call cannot use william.thunderlord's account in dev Sam. Sam will return 500, as it
    // cannot handle his tens of thousands of workspaces.
    UUID workspaceId = createWorkspaceSecondaryUser();
    List<UUID> samWorkspaceIdList =
        samService.listWorkspaceIds(userAccessUtils.secondUserAuthRequest());
    assertTrue(samWorkspaceIdList.contains(workspaceId));
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
    return createWorkspaceForUser(defaultUserRequest());
  }

  private UUID createWorkspaceSecondaryUser() {
    return createWorkspaceForUser(secondaryUserRequest());
  }

  private UUID createWorkspaceForUser(AuthenticatedUserRequest userReq) {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .jobId(UUID.randomUUID().toString())
            .build();
    return workspaceService.createWorkspace(request, userReq);
  }
}
