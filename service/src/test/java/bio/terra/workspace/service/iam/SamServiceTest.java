package bio.terra.workspace.service.iam;

import static bio.terra.workspace.common.mocks.MockDataRepoApi.CREATE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.*;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1;
import static bio.terra.workspace.common.mocks.MockWorkspaceV1Api.WORKSPACES_V1_GRANT_ROLE;
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
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCreateDataRepoSnapshotReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiGrantRoleRequestBody;
import bio.terra.workspace.generated.model.ApiReferenceResourceCommonFields;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.model.AccessibleWorkspace;
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
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyMembershipRequest;
import org.broadinstitute.dsde.workbench.client.sam.model.CreateResourceRequestV2;
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
  @Autowired private UserAccessUtils userAccessUtils;
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
        .perform(addAuth(get(String.format(WORKSPACES_V1, workspaceUuid)), secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    // After being granted permission, secondary user can read the workspace.
    mockMvc
        .perform(addAuth(get(String.format(WORKSPACES_V1, workspaceUuid)), secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_OK));
  }

  @Test
  void addedWriterCanWrite() throws Exception {
    ReferencedDataRepoSnapshotResource requestResource =
        ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
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
                        post(
                            String.format(
                                CREATE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT, workspaceUuid)),
                        secondaryUserRequest()))
                .content(objectMapper.writeValueAsString(referenceRequest)))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));

    // After being granted permission, secondary user can modify the workspace.
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                        addAuth(
                            post(
                                String.format(
                                    CREATE_REFERENCED_DATA_REPO_SNAPSHOTS_PATH_FORMAT,
                                    workspaceUuid)),
                            secondaryUserRequest()))
                    .content(objectMapper.writeValueAsString(referenceRequest)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiDataRepoSnapshotResource response =
        objectMapper.readValue(serializedResponse, ApiDataRepoSnapshotResource.class);

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
        .perform(addAuth(get(String.format(WORKSPACES_V1, workspaceUuid)), secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
    // After being granted permission, secondary user can read the workspace.
    samService.grantWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    mockMvc
        .perform(addAuth(get(String.format(WORKSPACES_V1, workspaceUuid)), secondaryUserRequest()))
        .andExpect(status().is(HttpStatus.SC_OK));
    // After removing permission, secondary user can no longer read.
    samService.removeWorkspaceRole(
        workspaceUuid,
        defaultUserRequest(),
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    mockMvc
        .perform(addAuth(get(String.format(WORKSPACES_V1, workspaceUuid)), secondaryUserRequest()))
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
                userAccessUtils.getSecondUserEmail()));
  }

  private void createBillingProjectResource(
      AuthenticatedUserRequest userRequest, String projectName) throws ApiException {
    ResourcesApi resourceApi = samService.samResourcesApi(userRequest.getRequiredToken());
    CreateResourceRequestV2 createResourceRequest =
        new CreateResourceRequestV2()
            .resourceId(projectName)
            .putPoliciesItem(
                "owner",
                new AccessPolicyMembershipRequest()
                    .addRolesItem("owner")
                    .addMemberEmailsItem(userRequest.getEmail()))
            .authDomain(List.of());
    resourceApi.createResourceV2("billing-project", createResourceRequest);
  }

  private void deleteBillingProjectResource(
      AuthenticatedUserRequest userRequest, String projectName) throws ApiException {
    ResourcesApi resourceApi = samService.samResourcesApi(userRequest.getRequiredToken());
    resourceApi.deleteResourceV2("billing-project", projectName);
  }

  @Test
  void projectOwnerRole() throws ApiException, InterruptedException {
    Workspace workspace = null;
    AuthenticatedUserRequest workspaceCreatorRequest = defaultUserRequest();
    AuthenticatedUserRequest billingProjectOwnerRequest = billingUserRequest();
    String billingProjectId = UUID.randomUUID().toString();
    try {
      // Create a Sam resource for the billing project (in Terra CWB production,
      // done in Rawls at billing project creation time)
      createBillingProjectResource(billingProjectOwnerRequest, billingProjectId);

      // Create a workspace, passing the optional billing project ID.
      workspace = createWorkspaceForUser(workspaceCreatorRequest, billingProjectId);

      // Verify creator has role "owner".
      AccessibleWorkspace workspaceCreatorRole =
          samService
              .listWorkspaceIdsAndHighestRoles(workspaceCreatorRequest, WsmIamRole.READER)
              .get(workspace.workspaceId());
      assertEquals(WsmIamRole.OWNER, workspaceCreatorRole.highestRole());

      // Verify billing project owner has role "project owner".
      AccessibleWorkspace billingProjectOwnerRole =
          samService
              .listWorkspaceIdsAndHighestRoles(billingProjectOwnerRequest, WsmIamRole.READER)
              .get(workspace.workspaceId());
      assertEquals(WsmIamRole.PROJECT_OWNER, billingProjectOwnerRole.highestRole());

    } finally {
      if (workspace != null) {
        workspaceService.deleteWorkspace(workspace, workspaceCreatorRequest);
      }
      deleteBillingProjectResource(billingProjectOwnerRequest, billingProjectId);
    }
  }

  @Test
  void permissionsApiFailsInRawlsWorkspace() throws Exception {
    UUID workspaceUuid = UUID.randomUUID();
    // RAWLS_WORKSPACEs do not own their own Sam resources, so we need to manage them separately.
    samService.createWorkspaceWithDefaults(
        defaultUserRequest(), workspaceUuid, new ArrayList<>(), null);

    Workspace rawlsWorkspace =
        WorkspaceFixtures.buildWorkspace(workspaceUuid, WorkspaceStage.RAWLS_WORKSPACE);
    workspaceService.createWorkspace(rawlsWorkspace, null, null, null, defaultUserRequest());
    ApiGrantRoleRequestBody request =
        new ApiGrantRoleRequestBody().memberEmail(userAccessUtils.getSecondUserEmail());
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(
                            WORKSPACES_V1_GRANT_ROLE, workspaceUuid, WsmIamRole.READER.toSamRole()))
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
        userAccessUtils.getSecondUserEmail());
    List<RoleBinding> policyList = samService.listRoleBindings(workspaceUuid, defaultUserRequest());

    RoleBinding expectedOwnerBinding =
        RoleBinding.builder()
            .role(WsmIamRole.OWNER)
            .users(Collections.singletonList(userAccessUtils.getDefaultUserEmail()))
            .build();
    RoleBinding expectedWriterBinding =
        RoleBinding.builder().role(WsmIamRole.WRITER).users(Collections.emptyList()).build();
    RoleBinding expectedReaderBinding =
        RoleBinding.builder()
            .role(WsmIamRole.READER)
            .users(Collections.singletonList(userAccessUtils.getSecondUserEmail()))
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
        userAccessUtils.getSecondUserEmail());
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
                userAccessUtils.getSecondUserEmail()));
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
    Map<UUID, AccessibleWorkspace> actual =
        samService.listWorkspaceIdsAndHighestRoles(
            userAccessUtils.defaultUserAuthRequest(), WsmIamRole.READER);

    AccessibleWorkspace match = actual.get(workspaceUuid);
    assertEquals(WsmIamRole.OWNER, match.highestRole());
    assertTrue(match.missingAuthDomainGroups().isEmpty());
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
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    samService.createControlledResource(bucketResource, null, null, null, defaultUserRequest());

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
            .bucketName(ControlledGcpResourceFixtures.uniqueBucketName())
            .common(commonFields)
            .build();

    samService.createControlledResource(
        bucketResource,
        ControlledResourceIamRole.EDITOR,
        userAccessUtils.getDefaultUserEmail(),
        null,
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
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    samService.createControlledResource(bucketResource, null, null, null, defaultUserRequest());
    // This duplicate call should complete without throwing.
    samService.createControlledResource(bucketResource, null, null, null, defaultUserRequest());
    // Delete the bucket so we can clean up the workspace.
    samService.deleteControlledResource(bucketResource, defaultUserRequest());
  }

  @Test
  void duplicateResourceDeleteIgnored() throws Exception {
    ControlledResource bucketResource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    samService.createControlledResource(bucketResource, null, null, null, defaultUserRequest());

    samService.deleteControlledResource(bucketResource, defaultUserRequest());
    samService.deleteControlledResource(bucketResource, defaultUserRequest());
  }

  @Test
  void checkAdminAuthz_throwsForbiddenException() {
    assertThrows(
        ForbiddenException.class,
        () -> samService.checkAdminAuthz(userAccessUtils.defaultUserAuthRequest()));
    assertThrows(
        ForbiddenException.class,
        () -> samService.checkAdminAuthz(userAccessUtils.secondUserAuthRequest()));
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

  private AuthenticatedUserRequest billingUserRequest() {
    return new AuthenticatedUserRequest(
        userAccessUtils.billingUser().getEmail(),
        null,
        Optional.of(userAccessUtils.billingUserAccessToken().getTokenValue()));
  }

  /** Create a workspace using the default test user for connected tests, return its ID. */
  private Workspace createWorkspaceDefaultUser() {
    return createWorkspaceForUser(defaultUserRequest(), null);
  }

  private Workspace createWorkspaceForUser(
      AuthenticatedUserRequest userRequest, String projectOwnerGroupId) {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(workspace, null, null, projectOwnerGroupId, userRequest);
    return workspace;
  }
}
