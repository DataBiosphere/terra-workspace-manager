package bio.terra.workspace.service.iam;

import bio.terra.workspace.app.configuration.external.SamConfiguration;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.generated.model.ApiSystemStatusSystems;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.RoleBinding;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceNames;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.stage.StageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.GoogleApi;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyMembershipV2;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyResponseEntry;
import org.broadinstitute.dsde.workbench.client.sam.model.CreateResourceRequestV2;
import org.broadinstitute.dsde.workbench.client.sam.model.FullyQualifiedResourceId;
import org.broadinstitute.dsde.workbench.client.sam.model.SubsystemStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SamService {
  private final SamConfiguration samConfig;
  private final ObjectMapper objectMapper;
  private final StageService stageService;

  private final Set<String> SAM_OAUTH_SCOPES = ImmutableSet.of("openid", "email", "profile");

  @Autowired
  public SamService(
      SamConfiguration samConfig, ObjectMapper objectMapper, StageService stageService) {
    this.samConfig = samConfig;
    this.objectMapper = objectMapper;
    this.stageService = stageService;
  }

  private final Logger logger = LoggerFactory.getLogger(SamService.class);

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return client.setBasePath(samConfig.getBasePath());
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
  public String getWsmServiceAccountToken() throws IOException {
    GoogleCredentials creds =
        GoogleCredentials.getApplicationDefault().createScoped(SAM_OAUTH_SCOPES);
    creds.refreshIfExpired();
    return creds.getAccessToken().getTokenValue();
  }

  /**
   * Register WSM's service account as a user in Sam if it isn't already. This is called on every
   * WSM startup, but should only need to register with Sam once per environment.
   */
  public void initialize() {
    String wsmAccessToken;
    try {
      wsmAccessToken = getWsmServiceAccountToken();
    } catch (IOException e) {
      // In cases where WSM is not running as a service account (e.g. unit tests), the above call
      // will throw. This can be ignored now and later when the credentials are used again.
      logger.warn("Failed to register WSM service account in Sam. This is expected for tests.", e);
      return;
    }

    UsersApi usersApi = samUsersApi(wsmAccessToken);
    if (!wsmServiceAccountRegistered(usersApi)) {
      registerWsmServiceAccount(usersApi);
    }
  }

  @VisibleForTesting
  public boolean wsmServiceAccountRegistered(UsersApi usersApi) {
    try {
      // getUserStatusInfo throws a 404 if the calling user is not registered, which will happen
      // the first time WSM is run in each environment.
      usersApi.getUserStatusInfo();
      logger.info("WSM service account already registered in Sam");
      return true;
    } catch (ApiException userStatusException) {
      if (userStatusException.getCode() == HttpStatus.NOT_FOUND.value()) {
        return false;
      } else {
        throw new SamApiException(userStatusException);
      }
    }
  }

  private void registerWsmServiceAccount(UsersApi usersApi) {
    try {
      usersApi.createUserV2();
    } catch (ApiException createUserException) {
      throw new SamApiException(
          "Error registering WSM service account with Sam", createUserException);
    }
  }

  /**
   * Wrapper around the Sam client to create a workspace resource in Sam.
   *
   * <p>This creates a workspace with the provided ID and requesting user as the sole Owner. Empty
   * reader and writer policies are also created. Errors from the Sam client will be thrown as
   * SamApiExceptions, which wrap the underlying error and expose its status code.
   */
  @Traced
  public void createWorkspaceWithDefaults(AuthenticatedUserRequest userReq, UUID id) {
    ResourcesApi resourceApi = samResourcesApi(userReq.getRequiredToken());
    // Sam will throw an error if no owner is specified, so the caller's email is required. It can
    // be looked up using the auth token if that's all the caller provides.
    String callerEmail =
        userReq.getEmail() == null
            ? getEmailFromToken(userReq.getRequiredToken())
            : userReq.getEmail();
    CreateResourceRequestV2 workspaceRequest =
        new CreateResourceRequestV2()
            .resourceId(id.toString())
            .policies(defaultWorkspacePolicies(callerEmail));
    try {
      resourceApi.createResourceV2(SamConstants.SAM_WORKSPACE_RESOURCE, workspaceRequest);
      logger.info("Created Sam resource for workspace {}", id);
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
  }

  /**
   * List all workspace IDs in Sam this user has access to. Note that in environments shared with
   * Rawls, some of these workspaces will be Rawls managed and WSM will not know about them.
   */
  @Traced
  public List<UUID> listWorkspaceIds(AuthenticatedUserRequest userReq) {
    ResourcesApi resourceApi = samResourcesApi(userReq.getRequiredToken());
    List<UUID> workspaceIds = new ArrayList<>();
    try {
      for (var resourceAndPolicy :
          resourceApi.listResourcesAndPolicies(SamConstants.SAM_WORKSPACE_RESOURCE)) {
        try {
          workspaceIds.add(UUID.fromString(resourceAndPolicy.getResourceId()));
        } catch (IllegalArgumentException e) {
          // WSM always uses UUIDs for workspace IDs, but this is not enforced in Sam and there are
          // old workspaces that don't use UUIDs. Any workspace with a non-UUID workspace ID is
          // ignored here.
          continue;
        }
      }
    } catch (ApiException samException) {
      throw new SamApiException(samException);
    }
    return workspaceIds;
  }

  @Traced
  public void deleteWorkspace(String authToken, UUID id) {
    ResourcesApi resourceApi = samResourcesApi(authToken);
    try {
      resourceApi.deleteResource(SamConstants.SAM_WORKSPACE_RESOURCE, id.toString());
      logger.info("Deleted Sam resource for workspace {}", id);
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
  }

  @Traced
  public boolean isAuthorized(
      String accessToken, String iamResourceType, String resourceId, String action) {
    ResourcesApi resourceApi = samResourcesApi(accessToken);
    try {
      return resourceApi.resourcePermission(iamResourceType, resourceId, action);
    } catch (ApiException samException) {
      throw new SamApiException(samException);
    }
  }

  @Traced
  public void workspaceAuthzOnly(
      AuthenticatedUserRequest userReq, UUID workspaceId, String action) {
    boolean isAuthorized =
        isAuthorized(
            userReq.getRequiredToken(),
            SamConstants.SAM_WORKSPACE_RESOURCE,
            workspaceId.toString(),
            action);
    if (!isAuthorized)
      throw new SamUnauthorizedException(
          String.format(
              "User %s is not authorized to %s workspace %s",
              userReq.getEmail(), action, workspaceId));
    else
      logger.info(
          "User {} is authorized to {} workspace {}", userReq.getEmail(), action, workspaceId);
  }

  /**
   * Wrapper around Sam client to grant a role to the provided user.
   *
   * <p>This operation is only available to MC_WORKSPACE stage workspaces, as Rawls manages
   * permissions directly on other workspaces.
   *
   * @param workspaceId The workspace this operation takes place in
   * @param userReq Credentials of the user requesting this operation. Only owners have permission
   *     to modify roles in a workspace.
   * @param role The role being granted.
   * @param email The user being granted a role.
   */
  @Traced
  public void grantWorkspaceRole(
      UUID workspaceId, AuthenticatedUserRequest userReq, WsmIamRole role, String email) {
    stageService.assertMcWorkspace(workspaceId, "grantWorkspaceRole");
    workspaceAuthzOnly(userReq, workspaceId, samActionToModifyRole(role));
    ResourcesApi resourceApi = samResourcesApi(userReq.getRequiredToken());
    try {
      resourceApi.addUserToPolicy(
          SamConstants.SAM_WORKSPACE_RESOURCE, workspaceId.toString(), role.toSamRole(), email);
      logger.info(
          "Granted role {} to user {} in workspace {}", role.toSamRole(), email, workspaceId);
    } catch (ApiException e) {
      throw new SamApiException(e);
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
      UUID workspaceId, AuthenticatedUserRequest userReq, WsmIamRole role, String email) {
    stageService.assertMcWorkspace(workspaceId, "removeWorkspaceRole");
    workspaceAuthzOnly(userReq, workspaceId, samActionToModifyRole(role));
    ResourcesApi resourceApi = samResourcesApi(userReq.getRequiredToken());
    try {
      resourceApi.removeUserFromPolicy(
          SamConstants.SAM_WORKSPACE_RESOURCE, workspaceId.toString(), role.toSamRole(), email);
      logger.info(
          "Removed role {} from user {} in workspace {}", role.toSamRole(), email, workspaceId);
    } catch (ApiException e) {
      throw new SamApiException(e);
    }
  }

  /**
   * Wrapper around Sam client to retrieve the current permissions model of a workspace.
   *
   * <p>This operation is only available to MC_WORKSPACE stage workspaces, as Rawls manages
   * permissions directly on other workspaces.
   */
  @Traced
  public List<RoleBinding> listRoleBindings(UUID workspaceId, AuthenticatedUserRequest userReq) {
    stageService.assertMcWorkspace(workspaceId, "listRoleBindings");
    workspaceAuthzOnly(userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_IAM_ACTION);
    ResourcesApi resourceApi = samResourcesApi(userReq.getRequiredToken());
    try {
      List<AccessPolicyResponseEntry> samResult =
          resourceApi.listResourcePolicies(
              SamConstants.SAM_WORKSPACE_RESOURCE, workspaceId.toString());
      return samResult.stream()
          .map(
              entry ->
                  RoleBinding.builder()
                      .role(WsmIamRole.fromSam(entry.getPolicyName()))
                      .users(entry.getPolicy().getMemberEmails())
                      .build())
          .collect(Collectors.toList());
    } catch (ApiException e) {
      throw new SamApiException(e);
    }
  }

  /**
   * Wrapper around Sam client to sync a Sam policy to a Google group. Returns email of that group.
   *
   * <p>This operation in Sam is idempotent, so we don't worry about calling this multiple times.
   */
  @Traced
  public String syncWorkspacePolicy(
      UUID workspaceId, WsmIamRole role, AuthenticatedUserRequest userReq) {
    GoogleApi googleApi = samGoogleApi(userReq.getRequiredToken());
    try {
      // Sam makes no guarantees about what values are returned from the POST call, so we instead
      // fetch the group in a separate call after syncing.
      googleApi.syncPolicy(
          SamConstants.SAM_WORKSPACE_RESOURCE, workspaceId.toString(), role.toSamRole());
      String groupEmail =
          googleApi
              .syncStatus(
                  SamConstants.SAM_WORKSPACE_RESOURCE, workspaceId.toString(), role.toSamRole())
              .getEmail();
      logger.info(
          "Synced role {} to google group {} in workspace {}",
          role.toSamRole(),
          groupEmail,
          workspaceId);
      return groupEmail;
    } catch (ApiException e) {
      throw new SamApiException(e);
    }
  }

  /**
   * Create a controlled resource in Sam.
   *
   * @param resource The WSM representation of the resource to create.
   * @param privateIamRoles The IAM role(s) to grant a private user. Required for private resources,
   *     should be null otherwise.
   * @param userReq Credentials to use for talking to Sam.
   */
  @Traced
  public void createControlledResource(
      ControlledResource resource,
      List<ControlledResourceIamRole> privateIamRoles,
      AuthenticatedUserRequest userReq) {
    ResourcesApi resourceApi = samResourcesApi(userReq.getRequiredToken());
    FullyQualifiedResourceId workspaceParentFqId =
        new FullyQualifiedResourceId()
            .resourceId(resource.getWorkspaceId().toString())
            .resourceTypeName(SamConstants.SAM_WORKSPACE_RESOURCE);
    CreateResourceRequestV2 resourceRequest =
        new CreateResourceRequestV2()
            .resourceId(resource.getResourceId().toString())
            .parent(workspaceParentFqId);

    addWsmResourceOwnerPolicy(resourceRequest);
    // Only create policies for private resources. Workspace role permissions are handled through
    // role-based inheritance in Sam instead. This will likely expand to include policies for
    // applications in the future.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      addPrivateResourcePolicies(
          resourceRequest, privateIamRoles, resource.getAssignedUser().get());
    }

    try {
      resourceApi.createResourceV2(
          SamControlledResourceNames.get(resource.getAccessScope(), resource.getManagedBy()),
          resourceRequest);
      logger.info("Created Sam controlled resource {}", resource.getResourceId());
    } catch (ApiException e) {
      throw new SamApiException(e);
    }
  }

  @Traced
  public void deleteControlledResource(
      ControlledResource resource, AuthenticatedUserRequest userReq) {
    ResourcesApi resourceApi = samResourcesApi(userReq.getRequiredToken());
    try {
      resourceApi.deleteResourceV2(
          SamControlledResourceNames.get(resource.getAccessScope(), resource.getManagedBy()),
          resource.getResourceId().toString());
      logger.info("Deleted Sam controlled resource {}", resource.getResourceId());
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
  }

  public ApiSystemStatusSystems status() {
    // No access token needed since this is an unauthenticated API.
    StatusApi statusApi = new StatusApi(getApiClient(null));

    try {
      // Note the SystemStatus class here is from the Sam client library, not generated by WM's
      // OpenAPI, so this requires some amount of reshaping objects.
      // Additionally, Sam's codegen API makes no guarantees about the shape of its subsystems,
      // so SystemStatus.getSystems() returns an Object. We serialize and de-serialize it to get
      // proper types.
      SystemStatus samStatus = statusApi.getSystemStatus();
      String serializedStatus = objectMapper.writeValueAsString(samStatus.getSystems());
      TypeReference<Map<String, SubsystemStatus>> typeRef = new TypeReference<>() {};
      Map<String, SubsystemStatus> subsystemStatusMap =
          objectMapper.readValue(serializedStatus, typeRef);
      List<String> subsystemStatusMessages =
          subsystemStatusMap.entrySet().stream()
              .map(
                  (entry) ->
                      entry.getKey() + ": " + StringUtils.join(entry.getValue().getMessages()))
              .collect(Collectors.toList());

      return new ApiSystemStatusSystems().ok(samStatus.getOk()).messages(subsystemStatusMessages);
    } catch (ApiException | JsonProcessingException e) {
      return new ApiSystemStatusSystems().ok(false).addMessagesItem(e.getLocalizedMessage());
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
    // For all non-owner roles, we create empty policies which can be modified later.
    for (WsmIamRole workspaceRole : WsmIamRole.values()) {
      if (workspaceRole != WsmIamRole.OWNER) {
        policyMap.put(
            workspaceRole.toSamRole(),
            new AccessPolicyMembershipV2().addRolesItem(workspaceRole.toSamRole()));
      }
    }
    return policyMap;
  }

  /**
   * Add WSM's service account as the owner of a controlled resource in Sam. Used for admin
   * reassignment of resources. This assumes samService.initialize() has already been called, which
   * should happen on start.
   */
  private void addWsmResourceOwnerPolicy(CreateResourceRequestV2 request) {
    try {
      String wsmSaEmail = getEmailFromToken(getWsmServiceAccountToken());
      AccessPolicyMembershipV2 ownerPolicy =
          new AccessPolicyMembershipV2()
              .addRolesItem(ControlledResourceIamRole.OWNER.toSamRole())
              .addMemberEmailsItem(wsmSaEmail);
      request.putPoliciesItem(ControlledResourceIamRole.OWNER.toSamRole(), ownerPolicy);
    } catch (IOException e) {
      // In cases where WSM is not running as a service account (e.g. unit tests), the above call to
      // get application default credentials will fail. This is fine, as those cases don't create
      // real resources.
      logger.warn(
          "Failed to add WSM service account as resource owner Sam. This is expected for tests.",
          e);
      return;
    }
  }

  /**
   * Add policies for managing private users via policy. All private resources will have reader,
   * writer, and editor policies even if they are empty to support potential later reassignment.
   * This method will likely expand to support policies for applications in the future.
   */
  private void addPrivateResourcePolicies(
      CreateResourceRequestV2 request,
      List<ControlledResourceIamRole> privateIamRoles,
      String privateUser) {
    AccessPolicyMembershipV2 readerPolicy =
        new AccessPolicyMembershipV2().addRolesItem(ControlledResourceIamRole.READER.toSamRole());
    AccessPolicyMembershipV2 writerPolicy =
        new AccessPolicyMembershipV2().addRolesItem(ControlledResourceIamRole.WRITER.toSamRole());
    AccessPolicyMembershipV2 editorPolicy =
        new AccessPolicyMembershipV2().addRolesItem(ControlledResourceIamRole.EDITOR.toSamRole());

    // Create a reader or writer role as specified by the user request, but also create empty
    // roles in case of later re-assignment.
    if (privateIamRoles.contains(ControlledResourceIamRole.WRITER)) {
      writerPolicy.addMemberEmailsItem(privateUser);
    } else if (privateIamRoles.contains(ControlledResourceIamRole.READER)) {
      readerPolicy.addMemberEmailsItem(privateUser);
    }

    if (privateIamRoles.contains(ControlledResourceIamRole.EDITOR)) {
      editorPolicy.addMemberEmailsItem(privateUser);
    }

    request.putPoliciesItem(ControlledResourceIamRole.READER.toSamRole(), readerPolicy);
    request.putPoliciesItem(ControlledResourceIamRole.WRITER.toSamRole(), writerPolicy);
    request.putPoliciesItem(ControlledResourceIamRole.EDITOR.toSamRole(), editorPolicy);
  }

  /** Fetch the email associated with an authToken from Sam. */
  private String getEmailFromToken(String authToken) {
    UsersApi usersApi = samUsersApi(authToken);
    try {
      return usersApi.getUserStatusInfo().getUserEmail();
    } catch (ApiException samException) {
      throw new SamApiException(samException);
    }
  }

  /** Returns the Sam action for modifying a given IAM role. */
  private String samActionToModifyRole(WsmIamRole role) {
    return String.format("share_policy::%s", role.toSamRole());
  }
}
