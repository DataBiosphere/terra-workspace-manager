package bio.terra.workspace.service.iam;

import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import bio.terra.common.sam.SamRetry;
import bio.terra.common.sam.exception.SamExceptionFactory;
import bio.terra.common.tracing.OkHttpClientTracingInterceptor;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.SamConfiguration;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.service.iam.model.AccessibleWorkspace;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.RoleBinding;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceCategory;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.AdminApi;
import org.broadinstitute.dsde.workbench.client.sam.api.AzureApi;
import org.broadinstitute.dsde.workbench.client.sam.api.GoogleApi;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyMembershipRequest;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyResponseEntryV2;
import org.broadinstitute.dsde.workbench.client.sam.model.CreateResourceRequestV2;
import org.broadinstitute.dsde.workbench.client.sam.model.FullyQualifiedResourceId;
import org.broadinstitute.dsde.workbench.client.sam.model.GetOrCreatePetManagedIdentityRequest;
import org.broadinstitute.dsde.workbench.client.sam.model.PolicyIdentifiers;
import org.broadinstitute.dsde.workbench.client.sam.model.UserIdInfo;
import org.broadinstitute.dsde.workbench.client.sam.model.UserResourcesResponse;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
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
 * InterruptedExceptions to be thrown. Outside of flights, use the Rethrow.onInterrupted. See
 * comment there for more detail.
 */
@Component
public class SamService {

  private static final Set<String> SAM_OAUTH_SCOPES = ImmutableSet.of("openid", "email", "profile");
  private static final List<String> PET_SA_OAUTH_SCOPES =
      ImmutableList.of(
          "openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");
  private static final Logger logger = LoggerFactory.getLogger(SamService.class);
  private final SamConfiguration samConfig;
  private final SamUserFactory samUserFactory;
  private final OkHttpClient commonHttpClient;
  private final FeatureConfiguration features;
  private boolean wsmServiceAccountInitialized;

  @Autowired
  public SamService(SamConfiguration samConfig, FeatureConfiguration features, SamUserFactory samUserFactory, OpenTelemetry openTelemetry) {
    this.samConfig = samConfig;
    this.samUserFactory = samUserFactory;
    this.features = features;
    this.wsmServiceAccountInitialized = false;
    this.commonHttpClient =
        new ApiClient()
            .getHttpClient()
            .newBuilder()
            .addInterceptor(new OkHttpClientTracingInterceptor(openTelemetry))
            .build();
  }

  private ApiClient getApiClient(String accessToken) {
    // OkHttpClient objects manage their own thread pools, so it's much more performant to share one
    // across requests.
    ApiClient apiClient =
        new ApiClient().setHttpClient(commonHttpClient).setBasePath(samConfig.getBasePath());
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  @VisibleForTesting
  public ResourcesApi samResourcesApi(String accessToken) {
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
  public AzureApi samAzureApi(String accessToken) {
    return new AzureApi(getApiClient(accessToken));
  }

  public AdminApi samAdminApi(String accessToken) {
    return new AdminApi(getApiClient(accessToken));
  }

  @WithSpan
  private boolean isAdmin(AuthenticatedUserRequest userRequest) throws InterruptedException {
    try {
      // If the user can successfully call sam admin api, the user has terra level admin access.
      SamRetry.retry(
          () ->
              samAdminApi(userRequest.getRequiredToken())
                  .adminGetUserByEmail(getUserEmailFromSam(userRequest)));
      return true;
    } catch (ApiException apiException) {
      logger.info(
          "Error checking admin permission in Sam. This is expected if requester is not SAM admin.",
          apiException);
      return false;
    }
  }

  public String getWsmServiceAccountToken() {
    try {
      if (features.isAzureControlPlaneEnabled()) {
        TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        // The Microsoft Authentication Library (MSAL) currently specifies offline_access, openid,
        // profile, and email by default in authorization and token requests.
        AccessToken token =
            credential
                .getToken(
                    new TokenRequestContext().addScopes("https://graph.microsoft.com/.default"))
                .block();
        return token.getToken();
      } else {
        GoogleCredentials creds =
            GoogleCredentials.getApplicationDefault().createScoped(SAM_OAUTH_SCOPES);
        creds.refreshIfExpired();
        return creds.getAccessToken().getTokenValue();
      }
    } catch (IOException e) {
      throw new InternalServerErrorException("Internal server error retrieving WSM credentials", e);
    }
  }

  /**
   * Fetch the email associated with user credentials directly from Sam. Call this method outside a
   * flight as we don't need to retry when `InterruptException` happens outside a flight.
   */
  public String getUserEmailFromSamAndRethrowOnInterrupt(AuthenticatedUserRequest userRequest) {
    return Rethrow.onInterrupted(() -> getUserEmailFromSam(userRequest), "Get user email from SAM");
  }

  /**
   * Fetch the email associated with user credentials directly from Sam. This will always call Sam
   * to fetch an email and will never read it from the AuthenticatedUserRequest. This is important
   * for calls made by pet service accounts, which will have a pet email in the
   * AuthenticatedUserRequest, but Sam will return the owner's email.
   */
  public String getUserEmailFromSam(AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    return getUserStatusInfo(userRequest).getUserEmail();
  }

  /** Fetch the Sam user associated with AuthenticatedUserRequest. */
  public SamUser getSamUser(AuthenticatedUserRequest userRequest) {
    return samUserFactory.from(
        new BearerToken(userRequest.getRequiredToken()), samConfig.getBasePath());
  }

  /** Fetch the Sam user associated with HttpServletRequest. */
  public SamUser getSamUser(HttpServletRequest request) {
    return samUserFactory.from(request, samConfig.getBasePath());
  }

  /** Fetch the user status info associated with the user credentials directly from Sam. */
  public UserStatusInfo getUserStatusInfo(AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    UsersApi usersApi = samUsersApi(userRequest.getRequiredToken());
    try {
      return SamRetry.retry(usersApi::getUserStatusInfo);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting user status info from Sam", apiException);
    }
  }

  /** Fetch a user-assigned managed identity from Sam by user email with WSM credentials. */
  public String getOrCreateUserManagedIdentityForUser(
      String userEmail, String subscriptionId, String tenantId, String managedResourceGroupId)
      throws InterruptedException {
    AzureApi azureApi = samAzureApi(getWsmServiceAccountToken());

    GetOrCreatePetManagedIdentityRequest request =
        new GetOrCreatePetManagedIdentityRequest()
            .subscriptionId(subscriptionId)
            .tenantId(tenantId)
            .managedResourceGroupName(managedResourceGroupId);
    try {
      return SamRetry.retry(
          () -> azureApi.getPetManagedIdentityForUser(userEmail.toLowerCase(), request));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create(
          "Error getting user assigned managed identity from Sam", apiException);
    }
  }

  /**
   * Gets proxy group email.
   *
   * <p>This takes in userEmail instead of AuthenticatedUserRequest because of
   * WorkspaceService.removeWorkspaceRoleFromUser(). When User A removes User B from workspace, we
   * want to get B's proxy group, not A's.
   */
  public String getProxyGroupEmail(String userEmail, String token) throws InterruptedException {
    GoogleApi googleApi = samGoogleApi(token);
    try {
      return SamRetry.retry(() -> googleApi.getProxyGroup(userEmail));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting proxy group from Sam", apiException);
    }
  }

  /**
   * Register WSM's service account as a user in Sam if it isn't already. This should only need to
   * register with Sam once per environment, so it is implemented lazily.
   */
  private void initializeWsmServiceAccount() throws InterruptedException {
    if (!wsmServiceAccountInitialized) {
      final String wsmAccessToken;
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
      SamRetry.retry(() -> usersApi.createUserV2(""));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create(
          "Error registering WSM service account with Sam", apiException);
    }
  }

  public List<FullyQualifiedResourceId> getWorkspaceChildResources(
      AuthenticatedUserRequest userRequest, UUID workspaceId) throws InterruptedException {
    var resourceApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      return SamRetry.retry(
          () -> resourceApi.listResourceChildren("workspace", workspaceId.toString()));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create(
          "Error retrieving workspace child resources in Sam", apiException);
    }
  }

  /**
   * Wrapper around the Sam client to create a workspace resource in Sam.
   *
   * <p>This creates a workspace with the provided ID and requesting user as the sole Owner. Empty
   * reader and writer policies are also created. Errors from the Sam client will be thrown as Sam
   * specific exception types.
   */
  @WithSpan
  public void createWorkspaceWithDefaults(
      AuthenticatedUserRequest userRequest,
      UUID uuid,
      List<String> authDomainList,
      @Nullable String projectOwnerGroupId)
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
            .resourceId(uuid.toString())
            .policies(defaultWorkspacePolicies(humanUserEmail, projectOwnerGroupId))
            .authDomain(authDomainList);
    try {
      SamRetry.retry(
          () -> resourceApi.createResourceV2(SamConstants.SamResource.WORKSPACE, workspaceRequest));
      logger.info("Created Sam resource for workspace {}", uuid);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error creating a Workspace resource in Sam", apiException);
    }
    dumpRoleBindings(
        SamConstants.SamResource.WORKSPACE, uuid.toString(), userRequest.getRequiredToken());
  }

  /**
   * List all workspace IDs in Sam this user has access to. Note that in environments shared with
   * Rawls, some of these workspaces will be Rawls managed and WSM will not know about them.
   *
   * <p>Additionally, Rawls may create additional roles that WSM does not know about. Those roles
   * will be ignored here.
   *
   * @return map from workspace ID to AccessibleWorkspace record
   */
  @WithSpan
  public Map<UUID, AccessibleWorkspace> listWorkspaceIdsAndHighestRoles(
      AuthenticatedUserRequest userRequest, WsmIamRole minimumHighestRoleFromRequest)
      throws InterruptedException {
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    Map<UUID, AccessibleWorkspace> result = new HashMap<>();
    try {
      List<UserResourcesResponse> userResourcesResponses =
          SamRetry.retry(
              () -> resourceApi.listResourcesAndPoliciesV2(SamConstants.SamResource.WORKSPACE));

      for (var userResourcesResponse : userResourcesResponses) {
        try {
          UUID workspaceId = UUID.fromString(userResourcesResponse.getResourceId());
          List<WsmIamRole> roles =
              userResourcesResponse.getDirect().getRoles().stream()
                  .map(WsmIamRole::fromSam)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList());

          // Skip workspaces with no roles. (That means there's a role this WSM doesn't know
          // about.)
          WsmIamRole.getHighestRole(workspaceId, roles)
              .ifPresent(
                  highestRole -> {
                    if (minimumHighestRoleFromRequest.roleAtLeastAsHighAs(highestRole)) {
                      result.put(
                          workspaceId,
                          new AccessibleWorkspace(
                              workspaceId,
                              highestRole,
                              ImmutableList.copyOf(
                                  userResourcesResponse.getMissingAuthDomainGroups())));
                    }
                  });
        } catch (IllegalArgumentException e) {
          // WSM always uses UUIDs for workspace IDs, but this is not enforced in Sam and there are
          // old workspaces that don't use UUIDs. Any workspace with a non-UUID workspace ID is
          // ignored here.
        }
      }
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error listing Workspace Ids in Sam", apiException);
    }
    return result;
  }

  @WithSpan
  public void deleteWorkspace(AuthenticatedUserRequest userRequest, UUID uuid)
      throws InterruptedException {
    String authToken = userRequest.getRequiredToken();
    ResourcesApi resourceApi = samResourcesApi(authToken);
    try {
      SamRetry.retry(
          () -> resourceApi.deleteResourceV2(SamConstants.SamResource.WORKSPACE, uuid.toString()));
      logger.info("Deleted Sam resource for workspace {}", uuid);
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

      // Diagnostic: if the error was "Cannot delete a resource with children" then fetch and
      // dump the remaining children. Make the Sam exception first, so we get the error message
      // text from inside the Sam response.
      ErrorReportException samException =
          SamExceptionFactory.create("Error deleting a workspace in Sam", apiException);
      if (apiException.getCode() == HttpStatus.BAD_REQUEST.value()
          && StringUtils.contains(
              samException.getMessage(), "Cannot delete a resource with children")) {
        try {
          List<FullyQualifiedResourceId> children =
              resourceApi.listResourceChildren(SamConstants.SamResource.WORKSPACE, uuid.toString());
          logger.error(
              "Found {} child resources in Sam while deleting workspace {}", children.size(), uuid);
          for (FullyQualifiedResourceId child : children) {
            logger.error(
                "  Workspace {} child {} ({})",
                uuid,
                child.getResourceTypeName(),
                child.getResourceId());
          }
        } catch (ApiException innerApiException) {
          logger.error("Failed to retrieve the list of workspace children", innerApiException);
        }
      }
      throw samException;
    }
  }

  @WithSpan
  public List<String> listResourceActions(
      AuthenticatedUserRequest userRequest, String resourceType, String resourceId)
      throws InterruptedException {
    String authToken = userRequest.getRequiredToken();
    ResourcesApi resourceApi = samResourcesApi(authToken);
    try {
      return SamRetry.retry(() -> resourceApi.resourceActionsV2(resourceType, resourceId));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error listing resources actions in Sam", apiException);
    }
  }

  @WithSpan
  public void addGroupsToAuthDomain(
      AuthenticatedUserRequest userRequest,
      String resourceType,
      String resourceId,
      List<String> groups)
      throws InterruptedException {
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    // TODO: [PF-2938] We should use the service account to add these groups.
    // ResourcesApi resourceApi = samResourcesApi(getWsmServiceAccountToken());
    try {
      SamRetry.retry(() -> resourceApi.patchAuthDomainV2(resourceType, resourceId, groups));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error adding group to auth domain in Sam", apiException);
    }
  }

  @WithSpan
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
  @WithSpan
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
   * @param type The Sam type of the workspace/resource being checked
   * @param uuid The ID of the resource being checked
   * @param action The action being checked on the resource
   */
  @WithSpan
  public void checkAuthz(
      AuthenticatedUserRequest userRequest, String type, String uuid, String action)
      throws InterruptedException {
    boolean isAuthorized = isAuthorized(userRequest, type, uuid, action);
    final String userEmail = getUserEmailFromSam(userRequest);
    if (!isAuthorized)
      throw new ForbiddenException(
          String.format(
              "User %s is not authorized to perform action %s on %s %s",
              userEmail, action, type, uuid));
    else logger.info("User {} is authorized to {} {} {}", userEmail, action, type, uuid);
  }

  /**
   * Wrapper around isAdmin which throws an appropriate exception if a user does not have admin
   * access.
   *
   * @param userRequest Credentials of the user whose permissions are being checked
   */
  @WithSpan
  public void checkAdminAuthz(AuthenticatedUserRequest userRequest) throws InterruptedException {
    boolean isAuthorized = isAdmin(userRequest);
    final String userEmail = getUserEmailFromSam(userRequest);
    if (!isAuthorized)
      throw new ForbiddenException(
          String.format("User %s is not authorized to perform admin action", userEmail));
    else logger.info("User {} is an authorized admin", userEmail);
  }

  /**
   * Wrapper around Sam client to grant a role to the provided user.
   *
   * <p>This operation is only available to MC_WORKSPACE stage workspaces, as Rawls manages
   * permissions directly on other workspaces.
   *
   * @param workspaceUuid The workspace this operation takes place in
   * @param userRequest Credentials of the user requesting this operation. Only owners have
   *     permission to modify roles in a workspace.
   * @param role The role being granted.
   * @param email The user being granted a role.
   */
  @WithSpan
  public void grantWorkspaceRole(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest, WsmIamRole role, String email)
      throws InterruptedException {
    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      // GCP always uses lowercase email identifiers, so we do the same here for consistency.
      SamRetry.retry(
          () ->
              resourceApi.addUserToPolicyV2(
                  SamConstants.SamResource.WORKSPACE,
                  workspaceUuid.toString(),
                  role.toSamRole(),
                  email.toLowerCase(),
                  /* body= */ null));
      logger.info(
          "Granted role {} to user {} in workspace {}", role.toSamRole(), email, workspaceUuid);
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
  @WithSpan
  public void removeWorkspaceRole(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest, WsmIamRole role, String email)
      throws InterruptedException {

    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      SamRetry.retry(
          () ->
              resourceApi.removeUserFromPolicyV2(
                  SamConstants.SamResource.WORKSPACE,
                  workspaceUuid.toString(),
                  role.toSamRole(),
                  email.toLowerCase()));
      logger.info(
          "Removed role {} from user {} in workspace {}", role.toSamRole(), email, workspaceUuid);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error removing workspace role in Sam", apiException);
    }
  }

  /**
   * Wrapper around the Sam client to remove a role from the provided user on a controlled resource.
   *
   * <p>Similar to {@link #removeWorkspaceRole}, but for controlled resources. This should only be
   * necessary for private resources, as users do not have individual roles on shared resources.
   *
   * <p>This call to Sam is made as the WSM SA, as users do not have permission to directly modify
   * IAM on resources.
   *
   * @param resource The resource to remove a role from
   * @param role The role to remove
   * @param email Email identifier of the user whose role is being removed.
   */
  @WithSpan
  public void removeResourceRole(
      ControlledResource resource, ControlledResourceIamRole role, String email)
      throws InterruptedException {

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
   * IAM on resources.
   *
   * @param resource The resource to restore a role to
   * @param role The role to restore
   * @param email Email identifier of the user whose role is being restored.
   */
  @WithSpan
  public void restoreResourceRole(
      ControlledResource resource, ControlledResourceIamRole role, String email)
      throws InterruptedException {

    try {
      ResourcesApi wsmSaResourceApi = samResourcesApi(getWsmServiceAccountToken());
      SamRetry.retry(
          () ->
              wsmSaResourceApi.addUserToPolicyV2(
                  resource.getCategory().getSamResourceName(),
                  resource.getResourceId().toString(),
                  role.toSamRole(),
                  email,
                  /* body= */ null));
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
  @WithSpan
  public List<RoleBinding> listRoleBindings(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) throws InterruptedException {

    ResourcesApi resourceApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      List<AccessPolicyResponseEntryV2> samResult =
          SamRetry.retry(
              () ->
                  resourceApi.listResourcePoliciesV2(
                      SamConstants.SamResource.WORKSPACE, workspaceUuid.toString()));
      return samResult.stream()
          // Don't include WSM's SA as a manager. This is true for all workspaces and not useful to
          // callers.
          .filter(entry -> !entry.getPolicyName().equals(WsmIamRole.MANAGER.toSamRole()))
          // RAWLS_WORKSPACE stage workspaces may have additional roles set by Rawls that WSM
          // doesn't understand, ignore those.
          .filter(entry -> WsmIamRole.fromSam(entry.getPolicyName()) != null)
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
  @WithSpan
  public List<String> listUsersWithWorkspaceRole(
      UUID workspaceUuid, WsmIamRole role, AuthenticatedUserRequest userRequest) {
    ResourcesApi resourcesApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      return resourcesApi
          .getPolicyV2(
              SamConstants.SamResource.WORKSPACE, workspaceUuid.toString(), role.toSamRole())
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

  /** Wrapper around Sam client to fetch requester roles on specified resource. */
  @WithSpan
  public List<WsmIamRole> listRequesterRoles(
      AuthenticatedUserRequest userRequest, String samResourceType, String resourceId) {
    ResourcesApi resourcesApi = samResourcesApi(userRequest.getRequiredToken());
    try {
      return resourcesApi.resourceRolesV2(samResourceType, resourceId).stream()
          .map(WsmIamRole::fromSam)
          // RAWLS_WORKSPACE stage workspaces may have additional roles set by Rawls that WSM
          // doesn't understand, ignore those.
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } catch (ApiException e) {
      throw SamExceptionFactory.create("Error retrieving requester resource roles from Sam", e);
    }
  }

  @WithSpan
  public boolean isApplicationEnabledInSam(
      UUID workspaceUuid, String email, AuthenticatedUserRequest userRequest) {
    // We detect that an application is enabled in Sam by checking if the application has
    // the create-controlled-application-private action on the workspace.
    try {
      ResourcesApi resourcesApi = samResourcesApi(userRequest.getRequiredToken());
      return resourcesApi.resourceActionV2(
          SamConstants.SamResource.WORKSPACE,
          workspaceUuid.toString(),
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
  @WithSpan
  public String syncWorkspacePolicy(
      UUID workspaceUuid, WsmIamRole role, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    String group =
        syncPolicyOnObject(
            SamConstants.SamResource.WORKSPACE,
            workspaceUuid.toString(),
            role.toSamRole(),
            userRequest);
    logger.info(
        "Synced workspace role {} to google group {} in workspace {}",
        role.toSamRole(),
        group,
        workspaceUuid);
    return group;
  }

  /**
   * Retrieve the email of a sync'd workspace policy. This is used during controlled resource
   * create.
   *
   * @param workspaceUuid workspace to use
   * @param role workspace role to lookup
   * @param userRequest userRequest
   * @return email of the sync'd policy group
   * @throws InterruptedException on shutdown during retry wait
   */
  public String getWorkspacePolicy(
      UUID workspaceUuid, WsmIamRole role, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    GoogleApi googleApi = samGoogleApi(userRequest.getRequiredToken());
    try {
      return SamRetry.retry(
              () ->
                  googleApi.syncStatus(
                      SamConstants.SamResource.WORKSPACE,
                      workspaceUuid.toString(),
                      role.toSamRole()))
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
  @WithSpan
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
      SamRetry.retry(
          () -> googleApi.syncPolicy(resourceTypeName, resourceId, policyName, /* body= */ null));
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
  @WithSpan
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
            .parent(workspaceParentFqId)
            .authDomain(List.of());

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
  @WithSpan
  public void deleteControlledResource(ControlledResource resource, String token)
      throws InterruptedException {
    logger.info("Deleting controlled resource {}", resource.getResourceId());
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
    } catch (Exception e) {
      logger.error("Caught unexpected exception deleting controlled resource", e);
    }
  }

  /**
   * Delete controlled resource with the user request
   *
   * @param resource the controlled resource whose Sam resource to delete
   * @param userRequest user performing the delete
   * @throws InterruptedException on thread interrupt
   */
  @WithSpan
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
      ControlledResource resource, String userEmail, AuthenticatedUserRequest userRequest) {

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
      return statusApi.getSystemStatus().getOk();
    } catch (ApiException e) {
      //  If any exception was thrown during the status check, return that the system is not OK.
      return false;
    }
  }

  /**
   * Builds a policy list with a single provided owner and empty reader, writer, project-owner, and
   * application policies.
   *
   * <p>This is a helper function for building the policy section of a request to create a workspace
   * resource in Sam. The provided user is granted the OWNER role and empty policies for reader,
   * writer, project-owner, and application are also included.
   *
   * <p>The empty policies are included because Sam requires all policies on a workspace to be
   * provided at creation time. Although policy membership can be modified later, policy creation
   * must happen at the same time as workspace resource creation.
   */
  private Map<String, AccessPolicyMembershipRequest> defaultWorkspacePolicies(
      String ownerEmail, @Nullable String projectOwnerGroupId) {
    Map<String, AccessPolicyMembershipRequest> policyMap = new HashMap<>();
    policyMap.put(
        WsmIamRole.OWNER.toSamRole(),
        new AccessPolicyMembershipRequest()
            .addRolesItem(WsmIamRole.OWNER.toSamRole())
            .addMemberEmailsItem(ownerEmail));
    // Optionally add a policy for project owner (used for billing projects in Terra CWB).
    if (projectOwnerGroupId != null) {
      policyMap.put(
          WsmIamRole.PROJECT_OWNER.toSamRole(),
          new AccessPolicyMembershipRequest()
              .addRolesItem(WsmIamRole.PROJECT_OWNER.toSamRole())
              .addMemberPoliciesItem(
                  new PolicyIdentifiers()
                      .resourceTypeName("billing-project")
                      .policyName("owner")
                      .resourceId(projectOwnerGroupId)));
    }
    // For all non-owner/manager roles, we create empty policies which can be modified later.
    for (WsmIamRole workspaceRole : WsmIamRole.values()) {
      if (!Set.of(WsmIamRole.OWNER, WsmIamRole.MANAGER, WsmIamRole.PROJECT_OWNER)
          .contains(workspaceRole)) {
        policyMap.put(
            workspaceRole.toSamRole(),
            new AccessPolicyMembershipRequest().addRolesItem(workspaceRole.toSamRole()));
      }
    }
    // We always give WSM's service account the 'manager' role for admin control of workspaces.
    String wsmSa = GcpUtils.getWsmSaEmail();
    policyMap.put(
        WsmIamRole.MANAGER.toSamRole(),
        new AccessPolicyMembershipRequest()
            .addRolesItem(WsmIamRole.MANAGER.toSamRole())
            .addMemberEmailsItem(wsmSa));
    return policyMap;
  }

  /**
   * Fetch the email of a user's pet service account in a given project. This request to Sam will
   * create the pet SA if it doesn't already exist.
   */
  public String getOrCreatePetSaEmail(String projectId, String token) throws InterruptedException {
    GoogleApi googleApi = samGoogleApi(token);
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
      String petEmail = getOrCreatePetSaEmail(projectId, userRequest.getRequiredToken());
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

  /**
   * Construct the email of an arbitrary user's pet service account in a given project. Unlike
   * {@code getOrCreatePetSaEmail}, this will not create the underlying service account. It may
   * return pet SA email if userEmail is a user. If userEmail is a group, returns Optional.empty().
   */
  public Optional<ServiceAccountName> constructUserPetSaEmail(
      String projectId, String userEmail, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    UsersApi usersApi = samUsersApi(userRequest.getRequiredToken());
    try {
      UserIdInfo userId = SamRetry.retry(() -> usersApi.getUserIds(userEmail));

      // If userId is null, userEmail is a group, not a user. (getUserIds returns 204 with no
      // response body, which translates to userID = null.)
      if (userId == null) {
        return Optional.empty();
      }
      String subjectId = userId.getUserSubjectId();
      String saEmail = String.format("pet-%s@%s.iam.gserviceaccount.com", subjectId, projectId);

      return Optional.of(ServiceAccountName.builder().email(saEmail).projectId(projectId).build());
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting user subject ID from Sam", apiException);
    }
  }

  /** Returns the Sam action for modifying a given IAM role. */
  private String samActionToModifyRole(WsmIamRole role) {
    return String.format("share_policy::%s", role.toSamRole());
  }
}
