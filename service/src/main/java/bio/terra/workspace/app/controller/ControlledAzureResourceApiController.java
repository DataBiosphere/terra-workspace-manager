package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.utils.ControllerUtils;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.generated.controller.ControlledAzureResourceApi;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureDiskResource;
import bio.terra.workspace.generated.model.ApiAzureIpResource;
import bio.terra.workspace.generated.model.ApiAzureNetworkResource;
import bio.terra.workspace.generated.model.ApiAzureVmResource;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureDiskRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureIpRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureStorageRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureNetworkRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureDisk;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureIp;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureStorage;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureNetwork;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureVm;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureVmResult;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceResult;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ControlledAzureResourceApiController implements ControlledAzureResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledGcpResourceApiController.class);

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final ControlledResourceService controlledResourceService;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final HttpServletRequest request;
  private final JobService jobService;

  @Autowired
  public ControlledAzureResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledResourceService controlledResourceService,
      SamService samService,
      WorkspaceService workspaceService,
      JobService jobService,
      HttpServletRequest request) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.controlledResourceService = controlledResourceService;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.request = request;
    this.jobService = jobService;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureDisk> createAzureDisk(
      UUID workspaceId, ApiCreateControlledAzureDiskRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(assignedUserFromBodyOrToken(body.getCommon(), userRequest))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .diskName(body.getAzureDisk().getName())
            .region(body.getAzureDisk().getRegion())
            .size(body.getAzureDisk().getSize())
            .build();

    List<ControlledResourceIamRole> privateRoles = privateRolesFromBody(body.getCommon());

    // TODO: make createDisk call async once we have things working e2e
    final var createdDisk =
        controlledResourceService.createDisk(
            resource, body.getAzureDisk(), privateRoles, userRequest);
    var response =
        new ApiCreatedControlledAzureDisk()
            .resourceId(createdDisk.getResourceId())
            .azureDisk(createdDisk.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureIp> createAzureIp(
      UUID workspaceId, @Valid ApiCreateControlledAzureIpRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(assignedUserFromBodyOrToken(body.getCommon(), userRequest))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .ipName(body.getAzureIp().getName())
            .region(body.getAzureIp().getRegion())
            .build();

    List<ControlledResourceIamRole> privateRoles = privateRolesFromBody(body.getCommon());

    final ControlledAzureIpResource createdIp =
        controlledResourceService.createIp(resource, body.getAzureIp(), privateRoles, userRequest);
    var response =
        new ApiCreatedControlledAzureIp()
            .resourceId(createdIp.getResourceId())
            .azureIp(createdIp.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureStorage> createAzureStorage(
      UUID workspaceId, @Valid ApiCreateControlledAzureStorageRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledAzureStorageResource resource =
        ControlledAzureStorageResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(assignedUserFromBodyOrToken(body.getCommon(), userRequest))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .storageAccountName(body.getAzureStorage().getName())
            .region(body.getAzureStorage().getRegion())
            .build();

    List<ControlledResourceIamRole> privateRoles = privateRolesFromBody(body.getCommon());

    final ControlledAzureStorageResource createdStorage =
        controlledResourceService.createStorage(
            resource, body.getAzureStorage(), privateRoles, userRequest);
    var response =
        new ApiCreatedControlledAzureStorage()
            .resourceId(createdStorage.getResourceId())
            .azureStorage(createdStorage.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> createAzureVm(
      UUID workspaceId, @Valid ApiCreateControlledAzureVmRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(assignedUserFromBodyOrToken(body.getCommon(), userRequest))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .vmName(body.getAzureVm().getName())
            .region(body.getAzureVm().getRegion())
            .vmSize(body.getAzureVm().getVmSize())
            .vmImageUri(body.getAzureVm().getVmImageUri())
            .ipId(body.getAzureVm().getIpId())
            .networkId(body.getAzureVm().getNetworkId())
            .diskId(body.getAzureVm().getDiskId())
            .build();

    List<ControlledResourceIamRole> privateRoles = privateRolesFromBody(body.getCommon());

    final String jobId =
        controlledResourceService.createVm(
            resource,
            body.getAzureVm(),
            privateRoles,
            body.getJobControl(),
            ControllerUtils.getAsyncResultEndpoint(
                request, body.getJobControl().getId(), "create-result"),
            userRequest);

    final ApiCreatedControlledAzureVmResult result =
        fetchCreateControlledAzureVmResult(jobId, userRequest);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> getCreateAzureVmResult(
      UUID workspaceId, String jobId) throws ApiException {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCreatedControlledAzureVmResult result =
        fetchCreateControlledAzureVmResult(jobId, userRequest);
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCreatedControlledAzureVmResult fetchCreateControlledAzureVmResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    final JobService.AsyncJobResult<ApiCreatedControlledAzureVm> jobResult =
        jobService.retrieveAsyncJobResult(jobId, ApiCreatedControlledAzureVm.class, userRequest);
    return new ApiCreatedControlledAzureVmResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .azureVm(jobResult.getResult());
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureNetwork> createAzureNetwork(
      UUID workspaceId, ApiCreateControlledAzureNetworkRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledAzureNetworkResource resource =
        ControlledAzureNetworkResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(assignedUserFromBodyOrToken(body.getCommon(), userRequest))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .networkName(body.getAzureNetwork().getName())
            .subnetName(body.getAzureNetwork().getSubnetName())
            .addressSpaceCidr(body.getAzureNetwork().getAddressSpaceCidr())
            .subnetAddressCidr(body.getAzureNetwork().getSubnetAddressCidr())
            .region(body.getAzureNetwork().getRegion())
            .build();

    List<ControlledResourceIamRole> privateRoles = privateRolesFromBody(body.getCommon());

    final ControlledAzureNetworkResource createdNetwork =
        controlledResourceService.createNetwork(
            resource, body.getAzureNetwork(), privateRoles, userRequest);
    var response =
        new ApiCreatedControlledAzureNetwork()
            .resourceId(createdNetwork.getResourceId())
            .azureNetwork(createdNetwork.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureIp(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info("deleteIp workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            ControllerUtils.getAsyncResultEndpoint(request, jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureDisk(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteDisk workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            ControllerUtils.getAsyncResultEndpoint(request, jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureVm(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteAzureVm workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            ControllerUtils.getAsyncResultEndpoint(request, jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureNetwork(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteNetwork workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            ControllerUtils.getAsyncResultEndpoint(request, jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiAzureIpResource> getAzureIp(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    try {
      ApiAzureIpResource response = controlledResource.castToAzureIpResource().toApiResource();
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (InvalidMetadataException ex) {
      throw new BadRequestException(
          String.format(
              "Resource %s in workspace %s is not a controlled Azure Ip.",
              resourceId, workspaceId));
    }
  }

  @Override
  public ResponseEntity<ApiAzureDiskResource> getAzureDisk(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    try {
      var response = controlledResource.castToAzureDiskResource().toApiResource();
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (InvalidMetadataException ex) {
      throw new BadRequestException(
          String.format(
              "Resource %s in workspace %s is not a controlled Azure Disk.",
              resourceId, workspaceId));
    }
  }

  @Override
  public ResponseEntity<ApiAzureVmResource> getAzureVm(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    try {
      var response = controlledResource.castToAzureVmResource().toApiResource();
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (InvalidMetadataException ex) {
      throw new BadRequestException(
          String.format(
              "Resource %s in workspace %s is not a controlled Azure Vm.",
              resourceId, workspaceId));
    }
  }

  @Override
  public ResponseEntity<ApiAzureNetworkResource> getAzureNetwork(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    try {
      var response = controlledResource.castToAzureNetworkResource().toApiResource();
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (InvalidMetadataException ex) {
      throw new BadRequestException(
          String.format(
              "Resource %s in workspace %s is not a controlled Azure Network.",
              resourceId, workspaceId));
    }
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureDiskResult(
      UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureIpResult(
      UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureVmResult(
      UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureNetworkResult(
      UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  private ResponseEntity<ApiDeleteControlledAzureResourceResult> getJobDeleteResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    final JobService.AsyncJobResult<Void> jobResult =
        jobService.retrieveAsyncJobResult(jobId, Void.class, userRequest);
    var response =
        new ApiDeleteControlledAzureResourceResult()
            .jobReport(jobResult.getJobReport())
            .errorReport(jobResult.getApiErrorReport());
    return new ResponseEntity<>(
        response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }

  // TODO: the following code is identical to that in the ControlledGcpResourceApiController
  // We should refactor this common code outside of these controllers.
  /**
   * Extract the assigned user from a request to create a controlled resource. This field is only
   * populated for private resources, but if a resource is private then a "null" value means "assign
   * this resource to the requesting user" and should be populated here.
   */
  private String assignedUserFromBodyOrToken(
      ApiControlledResourceCommonFields commonFields, AuthenticatedUserRequest userRequest) {
    if (commonFields.getAccessScope() != ApiAccessScope.PRIVATE_ACCESS) {
      return null;
    }
    return Optional.ofNullable(commonFields.getPrivateResourceUser())
        .map(ApiPrivateResourceUser::getUserName)
        .orElseGet(
            () ->
                SamRethrow.onInterrupted(
                    () -> samService.getUserEmailFromSam(userRequest), "getUserEmailFromSam"));
  }

  /**
   * Extract a list of ControlledResourceIamRoles from the common fields of a controlled resource
   * request body, and validate that it's shaped appropriately for the specified AccessScopeType.
   *
   * <p>Shared access resources must not specify private resource roles. Private access resources
   * must specify at least one private resource role.
   */
  public static List<ControlledResourceIamRole> privateRolesFromBody(
      ApiControlledResourceCommonFields commonFields) {
    List<ControlledResourceIamRole> privateRoles =
        Optional.ofNullable(commonFields.getPrivateResourceUser())
            .map(
                user ->
                    user.getPrivateResourceIamRoles().stream()
                        .map(ControlledResourceIamRole::fromApiModel)
                        .collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    // Validate that we get the private role when the resource is private and do not get it
    // when the resource is public
    AccessScopeType accessScope = AccessScopeType.fromApi(commonFields.getAccessScope());
    if (accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE && privateRoles.isEmpty()) {
      throw new ValidationException("At least one IAM role is required for private resources");
    }
    if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED && !privateRoles.isEmpty()) {
      throw new ValidationException(
          "Private resource IAM roles are not allowed for shared resources");
    }
    return privateRoles;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
