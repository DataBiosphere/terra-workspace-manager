package bio.terra.workspace.service.privateresource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.sam.exception.SamExceptionFactory;
import bio.terra.workspace.app.configuration.external.PrivateResourceCleanupConfiguration;
import bio.terra.workspace.app.configuration.external.SamConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.controlled.PrivateResourceState;
import bio.terra.workspace.service.resource.controlled.PrivateUserRole;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.GroupApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

public class PrivateResourceCleanupServiceTest extends BaseConnectedTest {

  // The name of the "member" policy created by default for groups. If this is ever used for
  // implementation code, it should live in SamConstants instead.
  private static final String SAM_GROUP_MEMBER_POLICY = "member";
  private Workspace workspace;

  @Autowired WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired SamConfiguration samConfiguration;
  @Autowired WorkspaceService workspaceService;
  @Autowired SamService samService;
  @Autowired ControlledResourceService controlledResourceService;
  @Autowired PrivateResourceCleanupConfiguration privateResourceCleanupConfiguration;
  @Autowired PrivateResourceCleanupService privateResourceCleanupService;
  @Autowired ResourceDao resourceDao;

  /** Set up default workspace. */
  @BeforeEach
  public void setupUserAndWorkspace() {
    workspace =
        workspaceUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());
  }

  /**
   * Delete workspace. Doing this outside of test bodies ensures cleanup runs even if tests fail.
   */
  @AfterEach
  private void cleanUpSharedWorkspace() {
    workspaceService.deleteWorkspace(
        workspace.getWorkspaceId(), userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void cleanupUserPrivateResource() {
    // Default user owns the workspace and group. Secondary user has workspace membership via group.
    String groupName = UUID.randomUUID().toString();
    GroupApi ownerGroupApi = buildGroupApi(userAccessUtils.defaultUserAuthRequest());
    // Add second user to group
    String groupEmail = createGroup(groupName, ownerGroupApi);
    addUserToGroup(groupName, userAccessUtils.getSecondUserEmail(), ownerGroupApi);
    // Add group to workspace as writer
    SamRethrow.onInterrupted(
        () ->
            samService.grantWorkspaceRole(
                workspace.getWorkspaceId(),
                userAccessUtils.defaultUserAuthRequest(),
                WsmIamRole.WRITER,
                groupEmail),
        "grantWorkspaceRole");
    // Create private bucket as second user.
    PrivateUserRole privateUserRole =
        new PrivateUserRole.Builder()
            .present(true)
            .userEmail(userAccessUtils.getSecondUserEmail())
            .role(ControlledResourceIamRole.EDITOR)
            .build();
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .assignedUser(userAccessUtils.getSecondUserEmail())
            .privateResourceState(PrivateResourceState.INITIALIZING)
            .build();
    ApiGcpGcsBucketCreationParameters creationParameters =
        new ApiGcpGcsBucketCreationParameters().location("us-central1");
    controlledResourceService.createBucket(
        resource,
        creationParameters,
        ControlledResourceIamRole.EDITOR,
        userAccessUtils.defaultUserAuthRequest());
    // Verify second user can read the private resource in Sam.
    SamRethrow.onInterrupted(
        () ->
            samService.checkAuthz(
                userAccessUtils.secondUserAuthRequest(),
                resource.getCategory().getSamResourceName(),
                resource.getResourceId().toString(),
                SamControlledResourceActions.READ_ACTION),
        "checkResourceAuth");
    // Remove second user from workspace via group
    removeUserFromGroup(groupName, userAccessUtils.getSecondUserEmail(), ownerGroupApi);
    // Verify second user is no longer in workspace, but still has resource access because cleanup
    // hasn't run yet.
    assertFalse(
        SamRethrow.onInterrupted(
            () ->
                samService.isAuthorized(
                    userAccessUtils.secondUserAuthRequest(),
                    SamResource.WORKSPACE,
                    resource.getWorkspaceId().toString(),
                    SamWorkspaceAction.READ),
            "checkResourceAuth"));
    assertTrue(
        SamRethrow.onInterrupted(
            () ->
                samService.isAuthorized(
                    userAccessUtils.secondUserAuthRequest(),
                    resource.getCategory().getSamResourceName(),
                    resource.getResourceId().toString(),
                    SamControlledResourceActions.READ_ACTION),
            "checkResourceAuth"));
    // Manually enable and run cleanup.
    privateResourceCleanupConfiguration.setEnabled(true);
    // Calling "cleanupResources" manually lets us skip waiting for the cronjob to trigger.
    privateResourceCleanupService.cleanupResources();
    // Verify second user can no longer read the resource.
    assertFalse(
        SamRethrow.onInterrupted(
            () ->
                samService.isAuthorized(
                    userAccessUtils.secondUserAuthRequest(),
                    resource.getCategory().getSamResourceName(),
                    resource.getResourceId().toString(),
                    SamControlledResourceActions.READ_ACTION),
            "checkResourceAuth"));
    // Verify resource is marked "abandonded"
    ControlledResource dbResource =
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getResourceId())
            .castToControlledResource();
    assertEquals(PrivateResourceState.ABANDONED, dbResource.getPrivateResourceState().get());
  }

  // TODO: test application-private resources once those are supported in connected tests.

  /**
   * Create a Sam managed group and return its email. This functionality is only used by tests, so
   * it lives here instead of in SamService.
   */
  private String createGroup(String groupName, GroupApi groupApi) {
    try {
      groupApi.postGroup(groupName);
      return groupApi.getGroup(groupName);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error checking creating group in Sam", e);
    }
  }

  private void addUserToGroup(String groupName, String userEmail, GroupApi groupApi) {
    try {
      groupApi.addEmailToGroup(groupName, SAM_GROUP_MEMBER_POLICY, userEmail);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error adding user to group in Sam", e);
    }
  }

  private void removeUserFromGroup(String groupName, String userEmail, GroupApi groupApi) {
    try {
      groupApi.removeEmailFromGroup(groupName, SAM_GROUP_MEMBER_POLICY, userEmail);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error removing user from group in Sam", e);
    }
  }

  private GroupApi buildGroupApi(AuthenticatedUserRequest userRequest) {
    // Each ApiClient manages its own threadpool. If we start using this method from more than a
    // handful of tests, we should refactor this to share the ApiClient object across tests.
    ApiClient apiClient = new ApiClient().setBasePath(samConfiguration.getBasePath());
    apiClient.setAccessToken(userRequest.getRequiredToken());
    return new GroupApi(apiClient);
  }
}
