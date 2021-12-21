package bio.terra.workspace.service.iam;

import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.common.sam.SamRetry;
import bio.terra.common.sam.exception.SamExceptionFactory;
import bio.terra.workspace.app.configuration.external.SamConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.RoleBinding;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceCategory;
import bio.terra.workspace.service.stage.StageService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import okhttp3.OkHttpClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.GoogleApi;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyMembershipV2;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyResponseEntry;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyResponseEntryV2;
import org.broadinstitute.dsde.workbench.client.sam.model.CreateResourceRequestV2;
import org.broadinstitute.dsde.workbench.client.sam.model.FullyQualifiedResourceId;
import org.broadinstitute.dsde.workbench.client.sam.model.ResourceAndAccessPolicy;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * SamService encapsulates logic for interacting with Sam. HTTP Statuses returned by Sam are
 * interpreted by the functions in this class.
 *
 * <p>This class is used both by Flights and outside of Flights. Flights need the
 * InterruptedExceptions to be thrown. Outside of flights, use the SamRethrow.onInterrupted. See
 * comment there for more detail.
 */
@Component
public class SamService {

  private final SamConfiguration samConfig;
  private final StageService stageService;
  private final OkHttpClient commonHttpClient;

  private final Set<String> SAM_OAUTH_SCOPES = ImmutableSet.of("openid", "email", "profile");
  private final List<String> PET_SA_OAUTH_SCOPES =
      ImmutableList.of(
          "openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");
  private final Logger logger = LoggerFactory.getLogger(SamService.class);
  private boolean wsmServiceAccountInitialized;

  @Autowired
  public SamService(SamConfiguration samConfig, StageService stageService) {
    this.samConfig = samConfig;
    this.stageService = stageService;
    this.wsmServiceAccountInitialized = false;
    this.commonHttpClient = new ApiClient().getHttpClient();
  }

  private ApiClient getApiClient(String accessToken) {
    // OkHttpClient objects manage their own thread pools, so it's much more performant to share one
    // across requests.
    ApiClient apiClient =
        new ApiClient().setHttpClient(commonHttpClient).setBasePath(samConfig.getBasePath());
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  private ResourcesApi samResourcesApi(String accessToken) {
    return new ResourcesApi(getApiClient(accessToken));
  }

  private GoogleApi samGoogleApi(String accessToken) {
    return new GoogleApi(getApiClient(accessToken));
  }

  @VisibleForTesting
  public UsersApi samUsersApi(String accessToken) {
    return new UsersApi(getApiClient(accessToken));
  }

  @VisibleForTesting
  public String getWsmServiceAccountToken() {
    try {
      GoogleCredentials creds =
          GoogleCredentials.getApplicationDefault().createScoped(SAM_OAUTH_SCOPES);
      creds.refreshIfExpired();
      return creds.getAccessToken().getTokenValue();
    } catch (IOException e) {
      throw new InternalServerErrorException("Internal server error retrieving WSM credentials", e);
    }
  }

  /**
   * Fetch the email associated with user credentials directly from Sam. Unlike {@code
   * getRequestUserEmail}, this will always call Sam to fetch an email and will never read it from
   * the AuthenticatedUserRequest. This is important for calls made by pet service accounts, which
   * will have a pet email in the AuthenticatedUserRequest, but Sam will return the owner's email.
   */
  public String getUserEmailFromSam(AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    UsersApi usersApi = samUsersApi(userRequest.getRequiredToken());
    try {
      return SamRetry.retry(() -> usersApi.getUserStatusInfo().getUserEmail());
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting user email from Sam", apiException);
    }
  }

  /**
   * Register WSM's service account as a user in Sam if it isn't already. This should only need to
   * register with Sam once per environment, so it is implemented lazily.
   */
  private void initializeWsmServiceAccount() throws InterruptedException {
    if (!wsmServiceAccountInitialized) {
      String wsmAccessToken = null;
      try {
        wsmAccessToken = getWsmServiceAccountToken();
      } catch (InternalServerErrorException e) {
        // In cases where WSM is not running as a service account (e.g. unit tests), the above call
        // will throw. This can be ignored now and later when the credentials are used again.
        logger.warn(
            "Failed to register WSM service account in Sam. This is expected for tests.", e);
        return;
      }
      UsersApi usersApi = samUsersApi(wsmAccessToken);
      // If registering the service account fails, all we can do is to keep trying.
      if (!wsmServiceAccountRegistered(usersApi)) {
        // retries internally
        registerWsmServiceAccount(usersApi);
      }
      wsmServiceAccountInitialized = true;
    }
  }

  @VisibleForTesting
  public boolean wsmServiceAccountRegistered(UsersApi usersApi) throws InterruptedException {
    try {
      // getUserStatusInfo throws a 404 if the calling user is not registered, which will happen
      // the first time WSM is run in each environment.
      SamRetry.retry(usersApi::getUserStatusInfo);
      logger.info("WSM service account already registered in Sam");
      return true;
    } catch (ApiException apiException) {
      if (apiException.getCode() == HttpStatus.NOT_FOUND.value()) {
        logger.info(
            "Sam error was NOT_FOUND when checking user registration. This means the "
                + " user is not registered but is not an exception. Returning false.");
        return false;
      } else {
        throw SamExceptionFactory.create("Error checking user status in Sam", apiException);
      }
    }
  }

  private void registerWsmServiceAccount(UsersApi usersApi) throws InterruptedException {
    try {
      SamRetry.retry(usersApi::createUserV2);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create(
          "Error registering WSM service account with Sam", apiException);
    }
  }

  /**
   * Wrapper around the Sam client to create a workspace resource in Sam.
   *
   * <p>This creates a workspace with the provided ID and requesting user as the sole Owner. Empty
   * reader and writer policies are also created. Errors from the Sam client will be thrown as Sam
   * specific exception types.
   */
  @Traced
  public void createWorkspaceWithDefaults(AuthenticatedUserRequest userRequest, UUID id)
      throws InterruptedException {
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    // Sam will throw an error if no owner is specified, so the caller's email is required. It can
    // be looked up using the auth token if that's all the caller provides.

    // If we called WSM as the pet SA and went through the proxy, this becomes the pet SA's email if
    // we use the request email. That caused an issue where the human user wasn't recognized on the
    // workspace.

    String humanUserEmail = getUserEmailFromSam(userRequest);
    CreateResourceRequestV2 workspaceRequest =
        new CreateResourceRequestV2()
            .resourceId(id.toString())
            .policies(defaultWorkspacePolicies(humanUserEmail));
    try {
      SamRetry.retry(
          () -> resourceApi.createResourceV2(SamConstants.SamResource.WORKSPACE, workspaceRequest));
      logger.info("Created Sam resource for workspace {}", id);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error creating a Workspace resource in Sam", apiException);
    }
    dumpRoleBindings(
        SamConstants.SamResource.WORKSPACE, id.toString(), userRequest.getRequiredToken());
  }

  /**
   * List all workspace IDs in Sam this user has access to. Note that in environments shared with
   * Rawls, some of these workspaces will be Rawls managed and WSM will not know about them.
   */
  @Traced
  public List<UUID> listWorkspaceIds(AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    List<UUID> workspaceIds = new ArrayList<>();
    try {
      List<ResourceAndAccessPolicy> resourceAndPolicies =
          SamRetry.retry(
              () -> resourceApi.listResourcesAndPolicies(SamConstants.SamResource.WORKSPACE));
      for (var resourceAndPolicy : resourceAndPolicies) {
        try {
          workspaceIds.add(UUID.fromString(resourceAndPolicy.getResourceId()));
        } catch (IllegalArgumentException e) {
          // WSM always uses UUIDs for workspace IDs, but this is not enforced in Sam and there are
          // old workspaces that don't use UUIDs. Any workspace with a non-UUID workspace ID is
          // ignored here.
          continue;
        }
      }
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error listing Workspace Ids in Sam", apiException);
    }
    return workspaceIds;
  }

  @Traced
  public void deleteWorkspace(AuthenticatedUserRequest userRequest, UUID id)
      throws InterruptedException {
    String authToken = userRequest.getRequiredToken();
    ResourcesApi resourceApi = samResourcesApi(authToken);
    try {
      SamRetry.retry(
          () -> resourceApi.deleteResource(SamConstants.SamResource.WORKSPACE, id.toString()));
      logger.info("Deleted Sam resource for workspace {}", id);
    } catch (ApiException apiException) {
      logger.info("Sam API error while deleting workspace, code is " + apiException.getCode());
      // Do nothing if the resource to delete is not found, this may not be the first time undo is
      // called. Other exceptions still need to be surfaced.
      if (apiException.getCode() == HttpStatus.NOT_FOUND.value()) {
        logger.info(
            "Sam error was NOT_FOUND on a deletion call. "
                + "This just means the deletion was tried twice so no error thrown.");
        return;
      }
      throw SamExceptionFactory.create("Error deleting a workspace in Sam", apiException);
    }
  }

  @Traced
  public boolean isAuthorized(
      AuthenticatedUserRequest userRequest,
      String iamResourceType,
      String resourceId,
      String action)
      throws InterruptedException {
    String accessToken = userRequest.getRequiredToken();
    ResourcesApi resourceApi = samResourcesApi(accessToken);
    try {
      return SamRetry.retry(
          () -> resourceApi.resourcePermissionV2(iamResourceType, resourceId, action));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error checking resource permission in Sam", apiException);
    }
  }

  /**
   * Check whether a user may perform an action on a Sam resource. Unlike {@code isAuthorized}, this
   * method does not require that the calling user and the authenticating user are the same - e.g.
   * user A may ask Sam whether user B has permission to perform an action.
   *
   * @param iamResourceType The type of the Sam resource to check
   * @param resourceId The ID of the Sam resource to check
   * @param action The action we're querying Sam for
   * @param userToCheck The email of the principle whose permission we are checking
   * @param userRequest Credentials for the call to Sam. These do not need to be from the same user
   *     as userToCheck.
   * @return True if userToCheck may perform the specified action on the specified resource. False
   *     otherwise.
   */
  @Traced
  public boolean userIsAuthorized(
      String iamResourceType,
      String resourceId,
      String action,
      String userToCheck,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      return SamRetry.retry(
          () -> resourceApi.resourceActionV2(iamResourceType, resourceId, action, userToCheck));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error checking resource permission in Sam", apiException);
    }
  }

  /**
   * Wrapper around {@code userIsAuthorized} which checks authorization using the WSM Service
   * Account's credentials rather than an end user's credentials. This should only be used when user
   * credentials are not available, as WSM's SA has permission to read all workspaces and resources.
   */
  public boolean checkAuthAsWsmSa(
      String iamResourceType, String resourceId, String action, String userToCheck)
      throws InterruptedException {
    String wsmSaToken = getWsmServiceAccountToken();
    AuthenticatedUserRequest wsmSaRequest =
        new AuthenticatedUserRequest().token(Optional.of(wsmSaToken));
    return userIsAuthorized(iamResourceType, resourceId, action, userToCheck, wsmSaRequest);
  }

  /**
   * Wrapper around isAuthorized which throws an appropriate exception if a user does not have
   * access to a resource. The wrapped call will perform a check for the appropriate permission in
   * Sam. This call answers the question "does user X have permission to do action Y on resource Z".
   *
   * @param userRequest Credentials of the user whose permissions are being checked
   * @param resourceType The Sam type of the resource being checked
   * @param resourceId The ID of the resource being checked
   * @param action The action being checked on the resource
   */
  @Traced
  public void checkAuthz(
      AuthenticatedUserRequest userRequest, String resourceType, String resourceId, String action)
      throws InterruptedException {
    boolean isAuthorized = isAuthorized(userRequest, resourceType, resourceId, action);
    final String userEmail = getUserEmailFromSam(userRequest);
    if (!isAuthorized)
      throw new ForbiddenException(
          String.format(
              "User %s is not authorized to %s resource %s of type %s",
              userEmail, action, resourceId, resourceType));
    else
      logger.info(
          "User {} is authorized to {} resource {} of type {}",
          userEmail,
          action,
          resourceId,
          resourceType);
  }

  /**
   * Wrapper around Sam client to grant a role to the provided user.
   *
   * <p>This operation is only available to MC_WORKSPACE stage workspaces, as Rawls manages
   * permissions directly on other workspaces.
   *
   * @param workspaceId The workspace this operation takes place in
   * @param userRequest Credentials of the user requesting this operation. Only owners have
   *     permission to modify roles in a workspace.
   * @param role The role being granted.
   * @param email The user being granted a role.
   */
  @Traced
  public void grantWorkspaceRole(
      UUID workspaceId, AuthenticatedUserRequest userRequest, WsmIamRole role, String email)
      throws InterruptedException {
    stageService.assertMcWorkspace(workspaceId, "grantWorkspaceRole");
    checkAuthz(
        userRequest,
        SamConstants.SamResource.WORKSPACE,
        workspaceId.toString(),
        samActionToModifyRole(role));
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      // GCP always uses lowercase email identifiers, so we do the same here for consistency.
      SamRetry.retry(
          () ->
              resourceApi.addUserToPolicy(
                  SamConstants.SamResource.WORKSPACE,
                  workspaceId.toString(),
                  role.toSamRole(),
                  email.toLowerCase()));
      logger.info(
          "Granted role {} to user {} in workspace {}", role.toSamRole(), email, workspaceId);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error granting workspace role in Sam", apiException);
    }
  }

  /**
   * Wrapper around Sam client to remove a role from the provided user.
   *
   * <p>This operation is only available to MC_WORKSPACE stage workspaces, as Rawls manages
   * permissions directly on other workspaces. Trying to remove a role that a user does not have
   * will succeed, though Sam will error if the email is not a registered user.
   */
  @Traced
  public void removeWorkspaceRole(
      UUID workspaceId, AuthenticatedUserRequest userRequest, WsmIamRole role, String email)
      throws InterruptedException {
    stageService.assertMcWorkspace(workspaceId, "removeWorkspaceRole");
    checkAuthz(
        userRequest,
        SamConstants.SamResource.WORKSPACE,
        workspaceId.toString(),
        samActionToModifyRole(role));
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      SamRetry.retry(
          () ->
              resourceApi.removeUserFromPolicy(
                  SamConstants.SamResource.WORKSPACE,
                  workspaceId.toString(),
                  role.toSamRole(),
                  email.toLowerCase()));
      logger.info(
          "Removed role {} from user {} in workspace {}", role.toSamRole(), email, workspaceId);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error removing workspace role in Sam", apiException);
    }
  }

  /**
   * Wrapper around the Sam client to remove a role from the provided user on a controlled resource.
   *
   * <p>Similar to {@removeWorkspaceRole}, but for controlled resources. This should only be
   * necessary for private resources, as users do not have individual roles on shared resources.
   *
   * <p>This call to Sam is made as the WSM SA, as users do not have permission to directly modify
   * IAM on resources. This method still requires user credentials to validate as a safeguard, but
   * they are not used in the role removal call.
   *
   * @param resource The resource to remove a role from
   * @param userRequest User credentials. These are not used for the call to Sam, but must belong to
   *     a workspace owner to ensure the WSM SA is being used on a user's behalf correctly.
   * @param role The role to remove
   * @param email Email identifier of the user whose role is being removed.
   */
  @Traced
  public void removeResourceRole(
      ControlledResource resource,
      AuthenticatedUserRequest userRequest,
      ControlledResourceIamRole role,
      String email)
      throws InterruptedException {
    // Validate that the provided user credentials can modify the owners of the resource's
    // workspace.
    // Although the Sam call to revoke a resource role must use WSM SA credentials instead, this
    // is a safeguard against accidentally invoking these credentials for unauthorized users.
    checkAuthz(
        userRequest,
        SamConstants.SamResource.WORKSPACE,
        resource.getWorkspaceId().toString(),
        samActionToModifyRole(WsmIamRole.OWNER));

    try {
      ResourcesApi wsmSaResourceApi = samResourcesApi(getWsmServiceAccountToken());
      SamRetry.retry(
          () ->
              wsmSaResourceApi.removeUserFromPolicyV2(
                  resource.getCategory().getSamResourceName(),
                  resource.getResourceId().toString(),
                  role.toSamRole(),
                  email));
      logger.info(
          "Removed role {} from user {} on resource {}",
          role.toSamRole(),
          email,
          resource.getResourceId());
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Sam error removing resource role in Sam", apiException);
    }
  }

  /**
   * Wrapper around the Sam client to restore a role to a user on a controlled resource. This is
   * only exposed to support undoing Stairway transactions which revoke access. It should not be
   * called otherwise.
   *
   * <p>This call to Sam is made as the WSM SA, as users do not have permission to directly modify
   * IAM on resources. This method still requires user credentials to validate as a safeguard, but
   * they are not used in the role removal call.
   *
   * @param resource The resource to restore a role to
   * @param userRequest User credentials. These are not used for the call to Sam, but must belong to
   *     a workspace owner to ensure the WSM SA is being used on a user's behalf correctly.
   * @param role The role to restore
   * @param email Email identifier of the user whose role is being restored.
   */
  @Traced
  public void restoreResourceRole(
      ControlledResource resource,
      AuthenticatedUserRequest userRequest,
      ControlledResourceIamRole role,
      String email)
      throws InterruptedException {
    // Validate that the provided user credentials can modify the owners of the resource's
    // workspace.
    // Although the Sam call to revoke a resource role must use WSM SA credentials instead, this
    // is a safeguard against accidentally invoking these credentials for unauthorized users.
    checkAuthz(
        userRequest,
        SamConstants.SamResource.WORKSPACE,
        resource.getWorkspaceId().toString(),
        samActionToModifyRole(WsmIamRole.OWNER));

    try {
      ResourcesApi wsmSaResourceApi = samResourcesApi(getWsmServiceAccountToken());
      SamRetry.retry(
          () ->
              wsmSaResourceApi.addUserToPolicyV2(
                  resource.getCategory().getSamResourceName(),
                  resource.getResourceId().toString(),
                  role.toSamRole(),
                  email));
      logger.info(
          "Restored role {} to user {} on resource {}",
          role.toSamRole(),
          email,
          resource.getResourceId());
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Sam error restoring resource role in Sam", apiException);
    }
  }

  /**
   * Wrapper around Sam client to retrieve the full current permissions model of a workspace.
   *
   * <p>This operation is only available to MC_WORKSPACE stage workspaces, as Rawls manages
   * permissions directly on other workspaces.
   */
  @Traced
  public List<RoleBinding> listRoleBindings(UUID workspaceId, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    stageService.assertMcWorkspace(workspaceId, "listRoleBindings");
    checkAuthz(
        userRequest,
        SamConstants.SamResource.WORKSPACE,
        workspaceId.toString(),
        SamConstants.SamWorkspaceAction.READ_IAM);
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      List<AccessPolicyResponseEntry> samResult =
          SamRetry.retry(
              () ->
                  resourceApi.listResourcePolicies(
                      SamConstants.SamResource.WORKSPACE, workspaceId.toString()));
      // Don't include WSM's SA as a manager. This is true for all workspaces and not useful to
      // callers.
      return samResult.stream()
          .filter(entry -> !entry.getPolicyName().equals(WsmIamRole.MANAGER.toSamRole()))
          .map(
              entry ->
                  RoleBinding.builder()
                      .role(WsmIamRole.fromSam(entry.getPolicyName()))
                      .users(entry.getPolicy().getMemberEmails())
                      .build())
          .collect(Collectors.toList());
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error listing role bindings in Sam", apiException);
    }
  }

  /** Wrapper around Sam client to fetch the list of users with a specific role in a workspace. */
  @Traced
  public List<String> listUsersWithWorkspaceRole(
      UUID workspaceId, WsmIamRole role, AuthenticatedUserRequest userRequest) {
    ResourcesApi resourcesApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      return resourcesApi
          .getPolicyV2(SamConstants.SamResource.WORKSPACE, workspaceId.toString(), role.toSamRole())
          .getMemberEmails();
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error retrieving workspace policy members from Sam", e);
    }
  }

  // Add code to retrieve and dump the role assignments for WSM controlled resources
  // for debugging. No permission check outside of Sam.
  public void dumpRoleBindings(String samResourceType, String resourceId, String token) {
    logger.debug("DUMP ROLE BINDING - resourceType {} resourceId {}", samResourceType, resourceId);

    ResourcesApi resourceApi = samResourcesApi(token);
    try {
      List<AccessPolicyResponseEntryV2> samResult =
          SamRetry.retry(() -> resourceApi.listResourcePoliciesV2(samResourceType, resourceId));
      for (AccessPolicyResponseEntryV2 entry : samResult) {
        logger.debug("  samPolicy: {}", entry);
      }
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error listing role bindings in Sam", apiException);
    } catch (InterruptedException e) {
      logger.warn("dump role binding was interrupted");
    }
  }

  @Traced
  public boolean isApplicationEnabledInSam(
      UUID workspaceId, String email, AuthenticatedUserRequest userRequest) {
    // We detect that an application is enabled in Sam by checking if the application has
    // the create-controlled-application-private action on the workspace.
    try {
      ResourcesApi resourcesApi = samResourcesApi(userRequest.getRequiredToken());
      return resourcesApi.resourceActionV2(
          SamConstants.SamResource.WORKSPACE,
          workspaceId.toString(),
          SamConstants.SamWorkspaceAction.CREATE_CONTROLLED_USER_PRIVATE,
          email);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Sam error querying role in Sam", apiException);
    }
  }

  /**
   * Wrapper around Sam client to sync a Sam policy to a Google group. Returns email of that group.
   *
   * <p>This operation in Sam is idempotent, so we don't worry about calling this multiple times.
   */
  @Traced
  public String syncWorkspacePolicy(
      UUID workspaceId, WsmIamRole role, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    String group =
        syncPolicyOnObject(
            SamConstants.SamResource.WORKSPACE,
            workspaceId.toString(),
            role.toSamRole(),
            userRequest);
    logger.info(
        "Synced workspace role {} to google group {} in workspace {}",
        role.toSamRole(),
        group,
        workspaceId);
    return group;
  }

  /**
   * Retrieve the email of a sync'd workspace policy. This is used during controlled resource
   * create.
   *
   * @param workspaceId workspace to use
   * @param role workspace role to lookup
   * @param userRequest
   * @return email of the sync'd policy group
   * @throws InterruptedException on shutdown during retry wait
   */
  public String getWorkspacePolicy(
      UUID workspaceId, WsmIamRole role, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    GoogleApi googleApi = samGoogleApi(userRequest.getRequiredToken());
    try {
      return SamRetry.retry(
              () ->
                  googleApi.syncStatus(
                      SamConstants.SamResource.WORKSPACE, workspaceId.toString(), role.toSamRole()))
          .getEmail();
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting sync policy in Sam", apiException);
    }
  }

  /**
   * Wrapper around Sam client to sync a Sam policy on a controlled resource to a google group and
   * return the email of that group.
   *
   * <p>This should only be called for controlled resources which require permissions granted to
   * individual users, i.e. user-private or application-controlled resources. All other cases are
   * handled by the permissions that workspace-level roles inherit on resources via Sam's
   * hierarchical resources, and do not use the policies synced by this function.
   *
   * <p>This operation in Sam is idempotent, so we don't worry about calling this multiple times.
   *
   * @param resource The resource to sync a binding for
   * @param role The policy to sync in Sam
   * @param userRequest User authentication
   * @return Sam policy group name
   */
  @Traced
  public String syncResourcePolicy(
      ControlledResource resource,
      ControlledResourceIamRole role,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    if (ControlledResourceCategory.get(resource.getAccessScope(), resource.getManagedBy())
        == ControlledResourceCategory.USER_SHARED) {
      throw new InternalLogicException(
          "syncResourcePolicy should not be called for USER managed SHARED access resources!");
    }

    String group =
        syncPolicyOnObject(
            resource.getCategory().getSamResourceName(),
            resource.getResourceId().toString(),
            role.toSamRole(),
            userRequest);
    logger.info(
        "Synced resource role {} to google group {} for resource {}",
        role.toSamRole(),
        group,
        resource.getResourceId());
    return group;
  }

  /**
   * Common implementation for syncing a policy to a Google group on an object in Sam.
   *
   * @param resourceTypeName The type of the Sam resource, as configured with Sam.
   * @param resourceId The Sam ID of the resource to sync a policy for
   * @param policyName The name of the policy to sync
   * @param userRequest User credentials to pass to Sam
   * @return The Google group whose membership is synced to the specified policy.
   */
  private String syncPolicyOnObject(
      String resourceTypeName,
      String resourceId,
      String policyName,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    GoogleApi googleApi = samGoogleApi(userRequest.getRequiredToken());
    try {
      // Sam makes no guarantees about what values are returned from the POST call, so we instead
      // fetch the group in a separate call after syncing.
      SamRetry.retry(() -> googleApi.syncPolicy(resourceTypeName, resourceId, policyName));
      return SamRetry.retry(() -> googleApi.syncStatus(resourceTypeName, resourceId, policyName))
          .getEmail();
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error syncing policy in Sam", apiException);
    }
  }

  /**
   * Create a controlled resource in Sam.
   *
   * @param resource The WSM representation of the resource to create.
   * @param privateIamRole The IAM role to grant on a private resource. It is required for
   *     user-private resources and optional for application-private resources.
   * @param assignedUserEmail Email identifier of the assigned user of this resource. Same
   *     constraints as privateIamRoles.
   * @param userRequest Credentials to use for talking to Sam.
   */
  @Traced
  public void createControlledResource(
      ControlledResource resource,
      @Nullable ControlledResourceIamRole privateIamRole,
      @Nullable String assignedUserEmail,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    // We need the WSM SA for setting controlled resource policies
    initializeWsmServiceAccount();
    FullyQualifiedResourceId workspaceParentFqId =
        new FullyQualifiedResourceId()
            .resourceId(resource.getWorkspaceId().toString())
            .resourceTypeName(SamConstants.SamResource.WORKSPACE);

    CreateResourceRequestV2 resourceRequest =
        new CreateResourceRequestV2()
            .resourceId(resource.getResourceId().toString())
            .parent(workspaceParentFqId);

    var builder =
        new ControlledResourceSamPolicyBuilder(
            this,
            privateIamRole,
            assignedUserEmail,
            userRequest,
            ControlledResourceCategory.get(resource.getAccessScope(), resource.getManagedBy()));
    builder.addPolicies(resourceRequest);

    try {
      // We use the user request for the create, but could equally well use the WSM SA.
      // The creating token has no effect on the resource policies.
      ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
      SamRetry.retry(
          () ->
              resourceApi.createResourceV2(
                  resource.getCategory().getSamResourceName(), resourceRequest));
      logger.info("Created Sam controlled resource {}", resource.getResourceId());

      dumpRoleBindings(
          resource.getCategory().getSamResourceName(),
          resource.getResourceId().toString(),
          getWsmServiceAccountToken());

    } catch (ApiException apiException) {
      // Do nothing if the resource to create already exists, this may not be the first time do is
      // called. Other exceptions still need to be surfaced.
      // Resource IDs are randomly generated, so we trust that the caller must have created
      // an existing Sam resource.
      logger.info(
          "Sam API error while creating a controlled resource, code is " + apiException.getCode());
      if (apiException.getCode() == HttpStatus.CONFLICT.value()) {
        logger.info(
            "Sam error was CONFLICT on creation request. This means the resource already "
                + "exists but is not an error so no exception thrown.");
        return;
      }
      throw SamExceptionFactory.create("Error creating controlled resource in Sam", apiException);
    }
  }

  /**
   * Delete controlled resource with an access token
   *
   * @param resource the controlled resource whose Sam resource to delete
   * @param token access token
   * @throws InterruptedException on thread interrupt
   */
  @Traced
  public void deleteControlledResource(ControlledResource resource, String token)
      throws InterruptedException {
    ResourcesApi resourceApi = samResourcesApi(token);
    try {
      SamRetry.retry(
          () ->
              resourceApi.deleteResourceV2(
                  resource.getCategory().getSamResourceName(),
                  resource.getResourceId().toString()));
      logger.info("Deleted Sam controlled resource {}", resource.getResourceId());
    } catch (ApiException apiException) {
      // Do nothing if the resource to delete is not found, this may not be the first time delete is
      // called. Other exceptions still need to be surfaced.
      logger.info(
          "Sam API error while deleting a controlled resource, code is " + apiException.getCode());
      if (apiException.getCode() == HttpStatus.NOT_FOUND.value()) {
        logger.info(
            "Sam error was NOT_FOUND on a deletion call. "
                + "This just means the deletion was tried twice so no error thrown.");
        return;
      }
      throw SamExceptionFactory.create("Error deleting controlled resource in Sam", apiException);
    }
  }

  /**
   * Delete controlled resource with the user request
   *
   * @param resource the controlled resource whose Sam resource to delete
   * @param userRequest user performing the delete
   * @throws InterruptedException on thread interrupt
   */
  @Traced
  public void deleteControlledResource(
      ControlledResource resource, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    deleteControlledResource(resource, userRequest.getRequiredToken());
  }

  /**
   * Return the list of roles a user has directly on a private, user-managed controlled resource.
   * This will not return roles that a user holds via group membership.
   *
   * <p>This call to Sam is made as the WSM SA, as users do not have permission to directly modify
   * IAM on resources. This method still requires user credentials to validate as a safeguard, but
   * they are not used in the role removal call.
   *
   * @param resource The resource to fetch roles on
   * @param userEmail Email identifier of the user whose role is being removed.
   * @param userRequest User credentials. These are not used for the call to Sam, but must belong to
   *     a workspace owner to ensure the WSM SA is being used on a user's behalf correctly.
   */
  public List<ControlledResourceIamRole> getUserRolesOnPrivateResource(
      ControlledResource resource, String userEmail, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    // Validate that the provided user credentials can modify the owners of the resource's
    // workspace.
    // Although the Sam call to revoke a resource role must use WSM SA credentials instead, this
    // is a safeguard against accidentally invoking these credentials for unauthorized users.
    checkAuthz(
        userRequest,
        SamConstants.SamResource.WORKSPACE,
        resource.getWorkspaceId().toString(),
        samActionToModifyRole(WsmIamRole.OWNER));

    try {
      ResourcesApi wsmSaResourceApi = samResourcesApi(getWsmServiceAccountToken());
      List<AccessPolicyResponseEntryV2> policyList =
          wsmSaResourceApi.listResourcePoliciesV2(
              resource.getCategory().getSamResourceName(), resource.getResourceId().toString());
      return policyList.stream()
          .filter(policyEntry -> policyEntry.getPolicy().getMemberEmails().contains(userEmail))
          .map(AccessPolicyResponseEntryV2::getPolicyName)
          .map(ControlledResourceIamRole::fromSamRole)
          .collect(Collectors.toList());
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Sam error removing resource role in Sam", apiException);
    }
  }

  public Boolean status() {
    // No access token needed since this is an unauthenticated API.
    StatusApi statusApi = new StatusApi(getApiClient(null));
    try {
      SystemStatus samStatus = SamRetry.retry(statusApi::getSystemStatus);
      return samStatus.getOk();
    } catch (ApiException | InterruptedException e) {
      //  If any exception was thrown during the status check, return that the system is not OK.
      return false;
    }
  }

  /**
   * Builds a policy list with a single provided owner and empty reader, writer and application
   * policies.
   *
   * <p>This is a helper function for building the policy section of a request to create a workspace
   * resource in Sam. The provided user is granted the OWNER role and empty policies for reader,
   * writer, and application are also included.
   *
   * <p>The empty policies are included because Sam requires all policies on a workspace to be
   * provided at creation time. Although policy membership can be modified later, policy creation
   * must happen at the same time as workspace resource creation.
   */
  private Map<String, AccessPolicyMembershipV2> defaultWorkspacePolicies(String ownerEmail) {
    Map<String, AccessPolicyMembershipV2> policyMap = new HashMap<>();
    policyMap.put(
        WsmIamRole.OWNER.toSamRole(),
        new AccessPolicyMembershipV2()
            .addRolesItem(WsmIamRole.OWNER.toSamRole())
            .addMemberEmailsItem(ownerEmail));
    // For all non-owner/manager roles, we create empty policies which can be modified later.
    for (WsmIamRole workspaceRole : WsmIamRole.values()) {
      if (workspaceRole != WsmIamRole.OWNER && workspaceRole != WsmIamRole.MANAGER) {
        policyMap.put(
            workspaceRole.toSamRole(),
            new AccessPolicyMembershipV2().addRolesItem(workspaceRole.toSamRole()));
      }
    }
    // We always give WSM's service account the 'manager' role for admin control of workspaces.
    String wsmSa = GcpUtils.getWsmSaEmail();
    policyMap.put(
        WsmIamRole.MANAGER.toSamRole(),
        new AccessPolicyMembershipV2()
            .addRolesItem(WsmIamRole.MANAGER.toSamRole())
            .addMemberEmailsItem(wsmSa));
    return policyMap;
  }

  /**
   * Add WSM's service account as the owner of a controlled resource in Sam. Used for admin
   * reassignment of resources. This assumes samService.initialize() has already been called, which
   * should happen on start.
   */
  private void addWsmResourceOwnerPolicy(CreateResourceRequestV2 request)
      throws InterruptedException {
    try {
      AuthenticatedUserRequest wsmRequest =
          new AuthenticatedUserRequest().token(Optional.of(getWsmServiceAccountToken()));
      String wsmSaEmail = getUserEmailFromSam(wsmRequest);
      AccessPolicyMembershipV2 ownerPolicy =
          new AccessPolicyMembershipV2()
              .addRolesItem(ControlledResourceIamRole.OWNER.toSamRole())
              .addMemberEmailsItem(wsmSaEmail);
      request.putPoliciesItem(ControlledResourceIamRole.OWNER.toSamRole(), ownerPolicy);
    } catch (InternalServerErrorException e) {
      // In cases where WSM is not running as a service account (e.g. unit tests), the above call to
      // get application default credentials will fail. This is fine, as those cases don't create
      // real resources.
      logger.warn(
          "Failed to add WSM service account as resource owner Sam. This is expected for tests.",
          e);
    }
  }

  /**
   * Fetch the email of a user's pet service account in a given project. This request to Sam will
   * create the pet SA if it doesn't already exist.
   */
  public String getOrCreatePetSaEmail(String projectId, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    GoogleApi googleApi = samGoogleApi(userRequest.getRequiredToken());
    try {
      return SamRetry.retry(() -> googleApi.getPetServiceAccount(projectId));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting pet service account from Sam", apiException);
    }
  }

  /**
   * Fetch credentials of a user's pet service account in a given project. This request to Sam will
   * create the pet SA if it doesn't already exist.
   */
  public AuthenticatedUserRequest getOrCreatePetSaCredentials(
      String projectId, AuthenticatedUserRequest userRequest) throws InterruptedException {
    GoogleApi samGoogleApi = samGoogleApi(userRequest.getRequiredToken());
    try {
      String petEmail = getOrCreatePetSaEmail(projectId, userRequest);
      String petToken =
          SamRetry.retry(
              () -> samGoogleApi.getPetServiceAccountToken(projectId, PET_SA_OAUTH_SCOPES));
      // This should never happen, but it's more informative than an NPE from Optional.of
      if (petToken == null) {
        throw new InternalServerErrorException("Sam returned null pet service account token");
      }
      return new AuthenticatedUserRequest().email(petEmail).token(Optional.of(petToken));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create(
          "Error getting pet service account token from Sam", apiException);
    }
  }

  // TODO(PF-991): This is a temporary workaround to support disabling pet service account
  //  self-impersonation without having user credentials available. When we stop granting this
  //  permission directly to users and their pets, this method should be deleted.
  /**
   * Construct the email of an arbitrary user's pet service account in a given project. Unlike
   * {@code getOrCreatePetSaEmail}, this will not create the underlying service account. It may
   * return the email of a service account which does not exist.
   */
  public ServiceAccountName constructUserPetSaEmail(
      String projectId, String userEmail, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    UsersApi usersApi = samUsersApi(userRequest.getRequiredToken());
    try {
      String subjectId = SamRetry.retry(() -> usersApi.getUserIds(userEmail).getUserSubjectId());
      String saEmail = String.format("pet-%s@%s.iam.gserviceaccount.com", subjectId, projectId);
      return ServiceAccountName.builder().email(saEmail).projectId(projectId).build();
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting user subject ID from Sam", apiException);
    }
  }

  /** Returns the Sam action for modifying a given IAM role. */
  private String samActionToModifyRole(WsmIamRole role) {
    return String.format("share_policy::%s", role.toSamRole());
  }
}
