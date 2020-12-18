package bio.terra.workspace.service.iam;

import bio.terra.workspace.app.configuration.external.SamConfiguration;
import bio.terra.workspace.app.configuration.spring.TraceInterceptorConfig;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.generated.model.SystemStatusSystems;
import bio.terra.workspace.service.iam.model.IamRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyMembership;
import org.broadinstitute.dsde.workbench.client.sam.model.CreateResourceRequest;
import org.broadinstitute.dsde.workbench.client.sam.model.SubsystemStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SamService {
  private final SamConfiguration samConfig;
  private final ObjectMapper objectMapper;

  @Autowired
  public SamService(SamConfiguration samConfig, ObjectMapper objectMapper) {
    this.samConfig = samConfig;
    this.objectMapper = objectMapper;
  }

  private Logger logger = LoggerFactory.getLogger(SamService.class);

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.addDefaultHeader(
        TraceInterceptorConfig.MDC_REQUEST_ID_HEADER,
        MDC.get(TraceInterceptorConfig.MDC_REQUEST_ID_KEY));
    client.setAccessToken(accessToken);
    return client.setBasePath(samConfig.getBasePath());
  }

  private ResourcesApi samResourcesApi(String accessToken) {
    return new ResourcesApi(getApiClient(accessToken));
  }

  private UsersApi samUsersApi(String accessToken) {
    return new UsersApi(getApiClient(accessToken));
  }

  /**
   * Wrapper around the Sam client to create a workspace resource in Sam.
   *
   * <p>This creates a workspace with the provided ID and requesting user as the sole Owner. Empty
   * reader and writer policies are also created. Errors from the Sam client will be thrown as
   * SamApiExceptions, which wrap the underlying error and expose its status code.
   */
  public void createWorkspaceWithDefaults(AuthenticatedUserRequest userReq, UUID id) {
    ResourcesApi resourceApi = samResourcesApi(userReq.getRequiredToken());
    // Sam will throw an error if no owner is specified, so the caller's email is required. It can
    // be looked up using the auth token if that's all the caller provides.
    String callerEmail =
        userReq.getEmail() == null
            ? getEmailFromToken(userReq.getRequiredToken())
            : userReq.getEmail();
    CreateResourceRequest workspaceRequest =
        new CreateResourceRequest()
            .resourceId(id.toString())
            .policies(defaultWorkspacePolicies(callerEmail));
    try {
      resourceApi.createResource(SamUtils.SAM_WORKSPACE_RESOURCE, workspaceRequest);
      logger.info(String.format("Created Sam resource for workspace %s", id.toString()));
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
  }

  public void deleteWorkspace(String authToken, UUID id) {
    ResourcesApi resourceApi = samResourcesApi(authToken);
    try {
      resourceApi.deleteResource(SamUtils.SAM_WORKSPACE_RESOURCE, id.toString());
      logger.info(String.format("Deleted Sam resource for workspace %s", id.toString()));
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
  }

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
  public void workspaceAuthz(AuthenticatedUserRequest userReq, UUID workspaceId, String action) {
    boolean isAuthorized =
        isAuthorized(
            userReq.getRequiredToken(),
            SamUtils.SAM_WORKSPACE_RESOURCE,
            workspaceId.toString(),
            action);
    if (!isAuthorized)
      throw new SamUnauthorizedException(
          String.format(
              "User %s is not authorized to %s workspace %s or it does not exist",
              userReq.getEmail(), action, workspaceId));
    else
      logger.info(
          String.format(
              "User %s is authorized to %s workspace %s",
              userReq.getEmail(), action, workspaceId.toString()));
  }

  public SystemStatusSystems status() {
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
      TypeReference<Map<String, SubsystemStatus>> typeRef =
          new TypeReference<Map<String, SubsystemStatus>>() {};
      Map<String, SubsystemStatus> subsystemStatusMap =
          objectMapper.readValue(serializedStatus, typeRef);
      List<String> subsystemStatusMessages =
          subsystemStatusMap.entrySet().stream()
              .map(
                  (entry) ->
                      entry.getKey() + ": " + StringUtils.join(entry.getValue().getMessages()))
              .collect(Collectors.toList());

      return new SystemStatusSystems().ok(samStatus.getOk()).messages(subsystemStatusMessages);
    } catch (ApiException | JsonProcessingException e) {
      return new SystemStatusSystems().ok(false).addMessagesItem(e.getLocalizedMessage());
    }
  }

  /**
   * Builds a policy list with a single provided owner and empty reader + writer policies.
   *
   * <p>This is a helper function for building the policy section of a request to create a workspace
   * resource in Sam. The provided user is granted the OWNER role and empty policies for reader and
   * writer are also included.
   *
   * <p>The empty policies are included because Sam requires all policies on a workspace to be
   * provided at creation time. Although policy membership can be modified later, policy creation
   * must happen at the same time as workspace resource creation.
   */
  private Map<String, AccessPolicyMembership> defaultWorkspacePolicies(String ownerEmail) {
    Map<String, AccessPolicyMembership> policyMap = new HashMap<>();
    policyMap.put(
        IamRole.OWNER.toSamRole(),
        new AccessPolicyMembership()
            .addRolesItem(IamRole.OWNER.toSamRole())
            .addMemberEmailsItem(ownerEmail));
    policyMap.put(
        IamRole.WRITER.toSamRole(),
        new AccessPolicyMembership().addRolesItem(IamRole.WRITER.toSamRole()));
    policyMap.put(
        IamRole.READER.toSamRole(),
        new AccessPolicyMembership().addRolesItem(IamRole.READER.toSamRole()));
    return policyMap;
  }

  /** Fetch the email associated with an authToken from Sam. */
  private String getEmailFromToken(String authToken) {
    UsersApi usersApi = samUsersApi(authToken);
    try {
      return usersApi.getUserStatusInfo().getUserEmail();
    } catch (ApiException e) {
      throw new SamApiException("Error fetching email from Sam: " + e);
    }
  }
}
