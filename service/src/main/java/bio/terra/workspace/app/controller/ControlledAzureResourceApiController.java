package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ApiException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.generated.controller.ControlledAzureResourceApi;
import bio.terra.workspace.generated.model.ApiAzureDiskResource;
import bio.terra.workspace.generated.model.ApiAzureIpResource;
import bio.terra.workspace.generated.model.ApiAzureNetworkResource;
import bio.terra.workspace.generated.model.ApiAzureVmResource;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureDiskRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureIpRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureNetworkRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureStorageRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureDisk;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureIp;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureNetwork;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureStorage;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureVm;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureVmResult;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceResult;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateUserRole;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ControlledAzureResourceApiController extends ControlledResourceControllerCommon
    implements ControlledAzureResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledGcpResourceApiController.class);

  private final ControlledResourceService controlledResourceService;
  private final JobService jobService;
  private final FeatureConfiguration features;

  @Autowired
  public ControlledAzureResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledResourceService controlledResourceService,
      SamService samService,
      JobService jobService,
      HttpServletRequest request,
      FeatureConfiguration features) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.controlledResourceService = controlledResourceService;
    this.jobService = jobService;
    this.features = features;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureDisk> createAzureDisk(
      UUID workspaceId, ApiCreateControlledAzureDiskRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, body.getCommon(), userRequest);

    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(privateUserRole.getUserEmail())
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .diskName(body.getAzureDisk().getName())
            .region(body.getAzureDisk().getRegion())
            .size(body.getAzureDisk().getSize())
            .build();

    // TODO: make createDisk call async once we have things working e2e
    final var createdDisk =
        controlledResourceService.createDisk(
            resource, body.getAzureDisk(), privateUserRole.getRole(), userRequest);
    var response =
        new ApiCreatedControlledAzureDisk()
            .resourceId(createdDisk.getResourceId())
            .azureDisk(createdDisk.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureIp> createAzureIp(
      UUID workspaceId, @Valid ApiCreateControlledAzureIpRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, body.getCommon(), userRequest);

    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(privateUserRole.getUserEmail())
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .ipName(body.getAzureIp().getName())
            .region(body.getAzureIp().getRegion())
            .build();

    final ControlledAzureIpResource createdIp =
        controlledResourceService.createIp(
            resource, body.getAzureIp(), privateUserRole.getRole(), userRequest);
    var response =
        new ApiCreatedControlledAzureIp()
            .resourceId(createdIp.getResourceId())
            .azureIp(createdIp.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureStorage> createAzureStorage(
      UUID workspaceId, @Valid ApiCreateControlledAzureStorageRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, body.getCommon(), userRequest);

    ControlledAzureStorageResource resource =
        ControlledAzureStorageResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(privateUserRole.getUserEmail())
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .storageAccountName(body.getAzureStorage().getName())
            .region(body.getAzureStorage().getRegion())
            .build();

    final ControlledAzureStorageResource createdStorage =
        controlledResourceService.createStorage(
            resource, body.getAzureStorage(), privateUserRole.getRole(), userRequest);
    var response =
        new ApiCreatedControlledAzureStorage()
            .resourceId(createdStorage.getResourceId())
            .azureStorage(createdStorage.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> createAzureVm(
      UUID workspaceId, @Valid ApiCreateControlledAzureVmRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, body.getCommon(), userRequest);

    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(privateUserRole.getUserEmail())
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

    final String jobId =
        controlledResourceService.createVm(
            resource,
            body.getAzureVm(),
            privateUserRole.getRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest);

    final ApiCreatedControlledAzureVmResult result =
        fetchCreateControlledAzureVmResult(jobId, userRequest);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> getCreateAzureVmResult(
      UUID workspaceId, String jobId) throws ApiException {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCreatedControlledAzureVmResult result =
        fetchCreateControlledAzureVmResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
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
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, body.getCommon(), userRequest);

    ControlledAzureNetworkResource resource =
        ControlledAzureNetworkResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(privateUserRole.getUserEmail())
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .networkName(body.getAzureNetwork().getName())
            .subnetName(body.getAzureNetwork().getSubnetName())
            .addressSpaceCidr(body.getAzureNetwork().getAddressSpaceCidr())
            .subnetAddressCidr(body.getAzureNetwork().getSubnetAddressCidr())
            .region(body.getAzureNetwork().getRegion())
            .build();

    final ControlledAzureNetworkResource createdNetwork =
        controlledResourceService.createNetwork(
            resource, body.getAzureNetwork(), privateUserRole.getRole(), userRequest);
    var response =
        new ApiCreatedControlledAzureNetwork()
            .resourceId(createdNetwork.getResourceId())
            .azureNetwork(createdNetwork.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureIp(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info("deleteIp workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureDisk(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteDisk workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureVm(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteAzureVm workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureNetwork(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteNetwork workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiAzureIpResource> getAzureIp(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureIpResource resource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_IP);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureDiskResource> getAzureDisk(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureDiskResource resource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureVmResource> getAzureVm(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureVmResource resource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_VM);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureNetworkResource> getAzureNetwork(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureNetworkResource resource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_NETWORK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureDiskResult(
      UUID workspaceId, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureIpResult(
      UUID workspaceId, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureVmResult(
      UUID workspaceId, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureNetworkResult(
      UUID workspaceId, String jobId) {
    features.azureEnabledCheck();
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
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }
}
