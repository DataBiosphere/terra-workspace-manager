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
import bio.terra.workspace.db.ApplicationDao;
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
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import com.google.auth.oauth2.AccessToken;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.GroupApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

// Use application configuration profile in addition to the standard connected test profile
// inherited from the base class.
@Tag("connectedPlus")
@ActiveProfiles({"app-test"})
public class PrivateResourceCleanupServiceTest extends BaseConnectedTest {

  // The name of the "member" policy created by default for groups. If this is ever used for
  // implementation code, it should live in SamConstants instead.
  private static final String SAM_GROUP_MEMBER_POLICY = "member";
  // Name of the test WSM application. This must match the identifier in the
  // application-app-test.yml file.
  private static final String TEST_WSM_APP = "TestWsmApp";

  private Workspace workspace;
  private GroupApi ownerGroupApi;
  private String groupName;
  private String groupEmail;

  @Autowired WorkspaceConnectedTestUtils workspaceUtils;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired SamConfiguration samConfiguration;
  @Autowired WorkspaceService workspaceService;
  @Autowired SamService samService;
  @Autowired ControlledResourceService controlledResourceService;
  @Autowired PrivateResourceCleanupConfiguration privateResourceCleanupConfiguration;
  @Autowired PrivateResourceCleanupService privateResourceCleanupService;
  @Autowired ResourceDao resourceDao;
  @Autowired ApplicationDao applicationDao;
  @Autowired WsmApplicationService wsmApplicationService;

  /** Set up default workspace, group ID, and GroupApi client object. */
  @BeforeEach
  public void setup() {
    // The cleanup cronjob will not run because it's initially disabled via configuration, but for
    // these tests we can enable it and manually trigger cleanup. No gap between runs to ensure
    // tests don't interfere with each other.
    privateResourceCleanupConfiguration.setEnabled(true);
    privateResourceCleanupConfiguration.setPollingInterval(Duration.ZERO);
    // Client API objects manage their own thread pools. If we add more tests, we should consider
    // factoring this into a shared object across tests.
    ownerGroupApi = buildGroupApi(userAccessUtils.defaultUserAuthRequest());
    workspace =
        workspaceUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());
    groupName = UUID.randomUUID().toString();
    groupEmail = createGroup(groupName, ownerGroupApi);
  }

  /**
   * Delete workspace. Doing this outside of test bodies ensures cleanup runs even if tests fail.
   */
  @AfterEach
  private void cleanup() {
    workspaceService.deleteWorkspace(workspace, userAccessUtils.defaultUserAuthRequest());
    deleteGroup(groupName, ownerGroupApi);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void cleanupResourcesSuppressExceptions_cleansUserPrivateResource_succeeds() {
    // Default user owns the workspace and group. Secondary user has workspace membership via group.
    // Add second user to group
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
    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
            .workspaceUuid(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .assignedUser(userAccessUtils.getSecondUserEmail())
            .build();
    ControlledGcsBucketResource resource =
        ControlledGcsBucketResource.builder()
            .common(commonFields)
            .bucketName(ControlledResourceFixtures.uniqueBucketName())
            .build();
    ApiGcpGcsBucketCreationParameters creationParameters =
        new ApiGcpGcsBucketCreationParameters().location("us-central1");
    controlledResourceService.createControlledResourceSync(
        resource,
        ControlledResourceIamRole.EDITOR,
        userAccessUtils.defaultUserAuthRequest(),
        creationParameters);
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
    privateResourceCleanupService.cleanupResourcesSuppressExceptions();
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
    // Verify resource is marked "abandoned"
    ControlledResource dbResource =
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getResourceId())
            .castToControlledResource();
    assertEquals(PrivateResourceState.ABANDONED, dbResource.getPrivateResourceState().get());
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = BUFFER_SERVICE_DISABLED_ENVS_REG_EX)
  void cleanupResourcesSuppressExceptions_cleansApplicationPrivateResource_succeeds() {
    // Default user owns the workspace and group. Secondary user has workspace membership via group.
    // Add second user to group
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
    // Enable the WSM test app in this workspace. This has a test user as the "service account" so
    // we can delegate credentials normally.
    WsmApplication app = applicationDao.getApplication(TEST_WSM_APP);
    AccessToken saAccessToken = userAccessUtils.generateAccessToken(app.getServiceAccount());
    AuthenticatedUserRequest appRequest =
        new AuthenticatedUserRequest()
            .email(app.getServiceAccount())
            .token(Optional.of(saAccessToken.getTokenValue()));

    wsmApplicationService.enableWorkspaceApplication(
        userAccessUtils.defaultUserAuthRequest(), workspace, TEST_WSM_APP);

    // Create application private bucket assigned to second user.
    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
            .workspaceUuid(workspace.getWorkspaceId())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_APPLICATION)
            .applicationId(TEST_WSM_APP)
            .assignedUser(userAccessUtils.getSecondUserEmail())
            .build();

    ControlledGcsBucketResource resource =
        ControlledGcsBucketResource.builder()
            .common(commonFields)
            .bucketName(ControlledResourceFixtures.uniqueBucketName())
            .build();

    ApiGcpGcsBucketCreationParameters creationParameters =
        new ApiGcpGcsBucketCreationParameters().location("us-central1");
    // Create resource as application.
    controlledResourceService.createControlledResourceSync(
        resource, ControlledResourceIamRole.WRITER, appRequest, creationParameters);

    // Verify second user can read the private resource in Sam.
    SamRethrow.onInterrupted(
        () ->
            samService.checkAuthz(
                userAccessUtils.secondUserAuthRequest(),
                resource.getCategory().getSamResourceName(),
                resource.getResourceId().toString(),
                SamControlledResourceActions.READ_ACTION),
        "checkResourceAuth");
    // Remove second user from workspace via group.
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
    privateResourceCleanupService.cleanupResourcesSuppressExceptions();
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
    // Verify resource is marked "abandoned"
    ControlledResource dbResource =
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getResourceId())
            .castToControlledResource();
    assertEquals(PrivateResourceState.ABANDONED, dbResource.getPrivateResourceState().get());
    // Application can still read the resource, because applications have EDITOR role on their
    // application-private resources.
    assertTrue(
        SamRethrow.onInterrupted(
            () ->
                samService.isAuthorized(
                    appRequest,
                    resource.getCategory().getSamResourceName(),
                    resource.getResourceId().toString(),
                    SamControlledResourceActions.READ_ACTION),
            "checkResourceAuth"));
  }

  /**
   * Create a Sam managed group and return its email. This functionality is only used by tests, so
   * it lives here instead of in SamService.
   */
  private static String createGroup(String groupName, GroupApi groupApi) {
    try {
      groupApi.postGroup(groupName, null);
      return groupApi.getGroup(groupName);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error creating group in Sam", e);
    }
  }

  private static void deleteGroup(String groupName, GroupApi groupApi) {
    try {
      groupApi.deleteGroup(groupName);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error deleting group in Sam", e);
    }
  }

  private static void addUserToGroup(String groupName, String userEmail, GroupApi groupApi) {
    try {
      groupApi.addEmailToGroup(groupName, SAM_GROUP_MEMBER_POLICY, userEmail, null);
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error adding user to group in Sam", e);
    }
  }

  private static void removeUserFromGroup(String groupName, String userEmail, GroupApi groupApi) {
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
