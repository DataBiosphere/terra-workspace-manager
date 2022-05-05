package bio.terra.workspace.service.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.sam.exception.SamBadRequestException;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.RoleBinding;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceCategory;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.StageDisabledException;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
  private UUID workspaceUuid;

  @BeforeEach
  public void setup() {
    doReturn(true).when(mockDataRepoService).snapshotReadable(any(), any(), any());
    workspaceUuid = createWorkspaceDefaultUser();
  }

  @AfterEach
  public void tearDown() {
    workspaceService.deleteWorkspace(workspaceUuid, defaultUserRequest());
  }

  @Test
  void samServiceInitializationSucceeded() throws Exception {
    // As part of initialization, WSM's service account is registered as a user in Sam. This test
    // verifies that registration status by calling Sam directly.
    String wsmAccessToken;
    wsmAccessToken = samService.getWsmServiceAccountToken();
    assertTrue(samService.wsmServiceAccountRegistered(samService.samUsersApi(wsmAccessToken)));
  }

  @Test
  void addedReaderCanRead() throws Exception {
    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        ForbiddenException.class,
        () -> workspaceService.getWorkspace(workspaceUuid, secondaryUserRequest()));
    // After being granted permission, secondary user can read the workspace.
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceUuid, secondaryUserRequest());
    assertEquals(workspaceUuid, readWorkspace.getWorkspaceId());
  }

  @Test
  void addedWriterCanWrite() throws Exception {
    ReferencedDataRepoSnapshotResource referenceResource =
        ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);

    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        ForbiddenException.class,
        () ->
            referenceResourceService.createReferenceResource(
                referenceResource, secondaryUserRequest()));

    // After being granted permission, secondary user can modify the workspace.
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());

    ReferencedResource ref =
        referenceResourceService.createReferenceResource(referenceResource, secondaryUserRequest());
    ReferencedDataRepoSnapshotResource resultResource =
        ref.castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    assertEquals(referenceResource, resultResource);
  }

  @Test
  void removedReaderCannotRead() throws Exception {
    // Before being granted permission, secondary user should be rejected.
    assertThrows(
        ForbiddenException.class,
        () -> workspaceService.getWorkspace(workspaceUuid, secondaryUserRequest()));
    // After being granted permission, secondary user can read the workspace.
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceUuid, secondaryUserRequest());
    assertEquals(workspaceUuid, readWorkspace.getWorkspaceId());
    // After removing permission, secondary user can no longer read.
    samService.removeWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    assertThrows(
        ForbiddenException.class,
        () -> workspaceService.getWorkspace(workspaceUuid, secondaryUserRequest()));
  }

  @Test
  void nonOwnerCannotAddReader() {
    // Note that this request uses the secondary user's authentication token, when only the first
    // user is an owner.
    assertThrows(
        ForbiddenException.class,
        () ->
            samService.grantWorkspaceRole(
                workspaceUuid,
                secondaryUserRequest(),
                WsmIamRole.READER,
                userAccessUtils.getSecondUserEmail()));
  }

  @Test
  void permissionsApiFailsInRawlsWorkspace() throws Exception {
    UUID workspaceUuid = UUID.randomUUID();
    // RAWLS_WORKSPACEs do not own their own Sam resources, so we need to manage them separately.
    samService.createWorkspaceWithDefaults(defaultUserRequest(), workspaceUuid);

    Workspace rawlsWorkspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId("a" + workspaceUuid.toString())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    workspaceService.createWorkspace(rawlsWorkspace, defaultUserRequest());
    assertThrows(
        StageDisabledException.class,
        () ->
            samService.grantWorkspaceRole(
                workspaceUuid,
                defaultUserRequest(),
                WsmIamRole.READER,
                userAccessUtils.getSecondUserEmail()));

    samService.deleteWorkspace(defaultUserRequest(), workspaceUuid);
  }

  @Test
  void invalidUserEmailRejected() {
    assertThrows(
        SamBadRequestException.class,
        () ->
            samService.grantWorkspaceRole(
                workspaceUuid,
                defaultUserRequest(),
                WsmIamRole.READER,
                "!!!INVALID EMAIL ADDRESS!!!!"));
  }

  @Test
  void listPermissionsIncludesAddedUsers() throws Exception {
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    List<RoleBinding> policyList = samService.listRoleBindings(workspaceUuid, defaultUserRequest());

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
  void writerCannotListPermissions() throws Exception {
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());
    assertThrows(
        ForbiddenException.class,
        () -> samService.listRoleBindings(workspaceUuid, secondaryUserRequest()));
  }

  @Test
  void grantRoleInMissingWorkspaceThrows() {
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
  void readRolesInMissingWorkspaceThrows() {
    UUID fakeId = UUID.randomUUID();
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> samService.listRoleBindings(fakeId, defaultUserRequest()));
  }

  @Test
  void listWorkspacesIncludesWsmWorkspace() throws Exception {
    List<UUID> samWorkspaceIdList =
        samService.listWorkspaceIds(userAccessUtils.defaultUserAuthRequest());
    assertTrue(samWorkspaceIdList.contains(workspaceUuid));
  }

  @Test
  void workspaceReaderIsSharedResourceReader() throws Exception {
    // Default user is workspace owner, secondary user is workspace reader
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());

    ControlledResource bucketResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    samService.createControlledResource(bucketResource, null, null, defaultUserRequest());

    // Workspace reader should have read access on a user-shared resource via inheritance
    assertTrue(
        samService.isAuthorized(
            secondaryUserRequest(),
            ControlledResourceCategory.USER_SHARED.getSamResourceName(),
            bucketResource.getResourceId().toString(),
            SamWorkspaceAction.READ));

    samService.deleteControlledResource(bucketResource, defaultUserRequest());
  }

  @Test
  void workspaceReaderIsNotPrivateResourceReader() throws Exception {
    // Default user is workspace owner, secondary user is workspace reader
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());

    // Create private resource assigned to the default user.
    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .assignedUser(userAccessUtils.getDefaultUserEmail())
            .privateResourceState(PrivateResourceState.ACTIVE)
            .build();

    ControlledResource bucketResource =
        ControlledGcsBucketResource.builder()
            .bucketName(ControlledResourceFixtures.uniqueBucketName())
            .common(commonFields)
            .build();

    samService.createControlledResource(
        bucketResource,
        ControlledResourceIamRole.EDITOR,
        userAccessUtils.getDefaultUserEmail(),
        defaultUserRequest());

    // Workspace reader should not have read access on a private resource.
    assertFalse(
        samService.isAuthorized(
            secondaryUserRequest(),
            ControlledResourceCategory.USER_PRIVATE.getSamResourceName(),
            bucketResource.getResourceId().toString(),
            SamConstants.SamWorkspaceAction.READ));
    // However, the assigned user should have read access.
    assertTrue(
        samService.isAuthorized(
            defaultUserRequest(),
            ControlledResourceCategory.USER_PRIVATE.getSamResourceName(),
            bucketResource.getResourceId().toString(),
            SamConstants.SamWorkspaceAction.READ));

    samService.deleteControlledResource(bucketResource, defaultUserRequest());
  }

  @Test
  void duplicateResourceCreateIgnored() throws Exception {
    ControlledResource bucketResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    samService.createControlledResource(bucketResource, null, null, defaultUserRequest());
    // This duplicate call should complete without throwing.
    samService.createControlledResource(bucketResource, null, null, defaultUserRequest());
    // Delete the bucket so we can clean up the workspace.
    samService.deleteControlledResource(bucketResource, defaultUserRequest());
  }

  @Test
  void duplicateResourceDeleteIgnored() throws Exception {
    ControlledResource bucketResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    samService.createControlledResource(bucketResource, null, null, defaultUserRequest());

    samService.deleteControlledResource(bucketResource, defaultUserRequest());
    samService.deleteControlledResource(bucketResource, defaultUserRequest());
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

  private UUID createWorkspaceForUser(AuthenticatedUserRequest userRequest) {
    UUID uuid = UUID.randomUUID();
    Workspace request =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId("a" + uuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, userRequest);
  }
}
