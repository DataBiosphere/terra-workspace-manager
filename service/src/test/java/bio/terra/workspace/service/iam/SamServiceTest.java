package bio.terra.workspace.service.iam;

import static bio.terra.workspace.common.testfixtures.ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder;
import static bio.terra.workspace.common.testfixtures.ControlledGcpResourceFixtures.uniqueBucketName;
import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static bio.terra.workspace.common.testfixtures.ReferenceResourceFixtures.makeDataRepoSnapshotResource;
import static bio.terra.workspace.common.testfixtures.WorkspaceFixtures.buildMcWorkspace;
import static bio.terra.workspace.common.testfixtures.WorkspaceFixtures.buildWorkspace;
import static bio.terra.workspace.common.testutils.MockMvcUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.sam.exception.SamBadRequestException;
import bio.terra.common.sam.exception.SamNotFoundException;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessTestUtils;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiGrantRoleRequestBody;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
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
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceDescription;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@Tag("connected")
class SamServiceTest extends BaseConnectedTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private SamService samService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private UserAccessTestUtils userAccessTestUtils;
  @Autowired private ReferencedResourceService referenceResourceService;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private DataRepoService mockDataRepoService;
  private Workspace workspace;
  private UUID workspaceUuid;

  @BeforeEach
  public void setup() {
    doReturn(true).when(mockDataRepoService).snapshotReadable(any(), any(), any());
    workspace = createWorkspaceDefaultUser();
    workspaceUuid = workspace.getWorkspaceId();
  }

  @AfterEach
  public void tearDown() {
    workspaceService.deleteWorkspace(workspace, defaultUserRequest());
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
    // Authz checks happen in the controller, so this uses mockMvc to pretend these are real
    // requests instead of hooking into the service layer directly.
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceUuid)),
                secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());
    // After being granted permission, secondary user can read the workspace.
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceUuid)),
                secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_OK));
  }

  @Test
  void addedWriterCanWrite() throws Exception {
    ReferencedDataRepoSnapshotResource requestResource =
        makeDataRepoSnapshotResource(workspaceUuid);
    ApiCreateDataRepoSnapshotReferenceRequestBody referenceRequest =
        new ApiCreateDataRepoSnapshotReferenceRequestBody()
            .metadata(
                new ApiReferenceResourceCommonFields()
                    .name(requestResource.getName())
                    .description(requestResource.getDescription())
                    .cloningInstructions(requestResource.getCloningInstructions().toApiModel()))
            .snapshot(requestResource.toApiAttributes());

    // Before being granted permission, secondary user should be rejected.
    mockMvc
        .perform(
            addJsonContentType(
                    addAuth(
                        post(String.format(CREATE_SNAPSHOT_PATH_FORMAT, workspaceUuid)),
                        secondaryUserRequest()))
                .content(objectMapper.writeValueAsString(referenceRequest)))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));

    // After being granted permission, secondary user can modify the workspace.
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.WRITER,
        userAccessTestUtils.getSecondUserEmail());
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                        addAuth(
                            post(String.format(CREATE_SNAPSHOT_PATH_FORMAT, workspaceUuid)),
                            secondaryUserRequest()))
                    .content(objectMapper.writeValueAsString(referenceRequest)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var response = objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);
    ReferencedResource ref =
        referenceResourceService.getReferenceResource(
            workspaceUuid, response.getMetadata().getResourceId());
    ReferencedDataRepoSnapshotResource resultResource =
        ref.castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    // The request and result objects will have different request IDs because callers can't specify
    // request ID, so just compare snapshot IDs to confirm the write was successful.
    assertEquals(requestResource.getSnapshotId(), resultResource.getSnapshotId());
  }

  @Test
  void removedReaderCannotRead() throws Exception {
    // Before being granted permission, secondary user should be rejected.
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceUuid)),
                secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
    // After being granted permission, secondary user can read the workspace.
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceUuid)),
                secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_OK));
    // After removing permission, secondary user can no longer read.
    samService.removeWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());
    mockMvc
        .perform(
            addAuth(
                get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, workspaceUuid)),
                secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
  }

  @Test
  void nonOwnerCannotAddReader() {
    // Note that this request uses the secondary user's authentication token, when only the first
    // user is an owner.
    assertThrows(
        SamNotFoundException.class,
        () ->
            samService.grantWorkspaceRole(
                workspaceUuid,
                secondaryUserRequest(),
                WsmIamRole.READER,
                userAccessTestUtils.getSecondUserEmail()));
  }

  @Test
  void permissionsApiFailsInRawlsWorkspace() throws Exception {
    UUID workspaceUuid = UUID.randomUUID();
    // RAWLS_WORKSPACEs do not own their own Sam resources, so we need to manage them separately.
    samService.createWorkspaceWithDefaults(defaultUserRequest(), workspaceUuid, new ArrayList<>());

    Workspace rawlsWorkspace = buildWorkspace(workspaceUuid, WorkspaceStage.RAWLS_WORKSPACE);
    workspaceService.createWorkspace(rawlsWorkspace, null, null, defaultUserRequest());
    ApiGrantRoleRequestBody request =
        new ApiGrantRoleRequestBody().memberEmail(userAccessTestUtils.getSecondUserEmail());
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(
                            ADD_USER_TO_WORKSPACE_PATH_FORMAT,
                            workspaceUuid,
                            WsmIamRole.READER.toSamRole()))
                        .content(objectMapper.writeValueAsString(request)),
                    defaultUserRequest())))
        .andExpect(status().is(HttpStatus.SC_BAD_REQUEST));

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
        userAccessTestUtils.getSecondUserEmail());
    List<RoleBinding> policyList = samService.listRoleBindings(workspaceUuid, defaultUserRequest());

    RoleBinding expectedOwnerBinding =
        RoleBinding.builder()
            .role(WsmIamRole.OWNER)
            .users(Collections.singletonList(userAccessTestUtils.getDefaultUserEmail()))
            .build();
    RoleBinding expectedWriterBinding =
        RoleBinding.builder().role(WsmIamRole.WRITER).users(Collections.emptyList()).build();
    RoleBinding expectedReaderBinding =
        RoleBinding.builder()
            .role(WsmIamRole.READER)
            .users(Collections.singletonList(userAccessTestUtils.getSecondUserEmail()))
            .build();
    RoleBinding expectedDiscovererBinding =
        RoleBinding.builder().role(WsmIamRole.DISCOVERER).users(Collections.emptyList()).build();
    RoleBinding expectedApplicationBinding =
        RoleBinding.builder().role(WsmIamRole.APPLICATION).users(Collections.emptyList()).build();
    assertThat(
        policyList,
        containsInAnyOrder(
            equalTo(expectedOwnerBinding),
            equalTo(expectedWriterBinding),
            equalTo(expectedReaderBinding),
            equalTo(expectedDiscovererBinding),
            equalTo(expectedApplicationBinding)));
  }

  @Test
  void writerCannotListPermissions() throws Exception {
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.WRITER,
        userAccessTestUtils.getSecondUserEmail());
    assertThrows(
        ForbiddenException.class,
        () -> samService.listRoleBindings(workspaceUuid, secondaryUserRequest()));
  }

  @Test
  void grantRoleInMissingWorkspaceThrows() {
    UUID fakeId = UUID.randomUUID();
    assertThrows(
        SamNotFoundException.class,
        () ->
            samService.grantWorkspaceRole(
                fakeId,
                defaultUserRequest(),
                WsmIamRole.READER,
                userAccessTestUtils.getSecondUserEmail()));
  }

  @Test
  void readRolesInMissingWorkspaceThrows() {
    UUID fakeId = UUID.randomUUID();
    assertThrows(
        SamNotFoundException.class,
        () -> samService.listRoleBindings(fakeId, defaultUserRequest()));
  }

  @Test
  void listWorkspaceIdsAndHighestRoles() throws Exception {
    Map<UUID, WorkspaceDescription> actual =
        samService.listWorkspaceIdsAndHighestRoles(
            userAccessTestUtils.defaultUserAuthRequest(), WsmIamRole.READER);

    WorkspaceDescription match = actual.get(workspaceUuid);
    assertEquals(WsmIamRole.OWNER, match.highestRole());
    assertTrue(match.missingAuthDomains().isEmpty());
  }

  @Test
  void workspaceReaderIsSharedResourceReader() throws Exception {
    // Default user is workspace owner, secondary user is workspace reader
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());

    ControlledResource bucketResource =
        makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
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
        userAccessTestUtils.getSecondUserEmail());

    // Create private resource assigned to the default user.
    ControlledResourceFields commonFields =
        makeDefaultControlledResourceFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .assignedUser(userAccessTestUtils.getDefaultUserEmail())
            .privateResourceState(PrivateResourceState.ACTIVE)
            .build();

    ControlledResource bucketResource =
        ControlledGcsBucketResource.builder()
            .bucketName(uniqueBucketName())
            .common(commonFields)
            .build();

    samService.createControlledResource(
        bucketResource,
        ControlledResourceIamRole.EDITOR,
        userAccessTestUtils.getDefaultUserEmail(),
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
        makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    samService.createControlledResource(bucketResource, null, null, defaultUserRequest());
    // This duplicate call should complete without throwing.
    samService.createControlledResource(bucketResource, null, null, defaultUserRequest());
    // Delete the bucket so we can clean up the workspace.
    samService.deleteControlledResource(bucketResource, defaultUserRequest());
  }

  @Test
  void duplicateResourceDeleteIgnored() throws Exception {
    ControlledResource bucketResource =
        makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    samService.createControlledResource(bucketResource, null, null, defaultUserRequest());

    samService.deleteControlledResource(bucketResource, defaultUserRequest());
    samService.deleteControlledResource(bucketResource, defaultUserRequest());
  }

  @Test
  void checkAdminAuthz_throwsForbiddenException() {
    assertThrows(
        ForbiddenException.class,
        () -> samService.checkAdminAuthz(userAccessTestUtils.defaultUserAuthRequest()));
    assertThrows(
        ForbiddenException.class,
        () -> samService.checkAdminAuthz(userAccessTestUtils.secondUserAuthRequest()));
  }

  /**
   * Convenience method to build an AuthenticatedUserRequest from utils' default user.
   *
   * <p>This only fills in access token, not email or subjectId.
   */
  private AuthenticatedUserRequest defaultUserRequest() {
    return new AuthenticatedUserRequest(
        null, null, Optional.of(userAccessTestUtils.defaultUserAccessToken().getTokenValue()));
  }

  /**
   * Convenience method to build an AuthenticatedUserRequest from utils' secondary default user.
   *
   * <p>This only fills in access token, not email or subjectId.
   */
  private AuthenticatedUserRequest secondaryUserRequest() {
    return new AuthenticatedUserRequest(
        null, null, Optional.of(userAccessTestUtils.secondUserAccessToken().getTokenValue()));
  }

  /** Create a workspace using the default test user for connected tests, return its ID. */
  private Workspace createWorkspaceDefaultUser() {
    return createWorkspaceForUser(defaultUserRequest());
  }

  private Workspace createWorkspaceForUser(AuthenticatedUserRequest userRequest) {
    Workspace workspace = buildMcWorkspace();
    workspaceService.createWorkspace(workspace, null, null, userRequest);
    return workspace;
  }
}
