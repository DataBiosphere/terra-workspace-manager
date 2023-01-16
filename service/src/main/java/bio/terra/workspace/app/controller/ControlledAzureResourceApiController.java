package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.AzureVmUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledAzureResourceApi;
import bio.terra.workspace.generated.model.ApiAzureDiskResource;
import bio.terra.workspace.generated.model.ApiAzureIpResource;
import bio.terra.workspace.generated.model.ApiAzureNetworkResource;
import bio.terra.workspace.generated.model.ApiAzureRelayNamespaceResource;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmResource;
import bio.terra.workspace.generated.model.ApiCloneControlledAzureStorageContainerRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledAzureStorageContainerResult;
import bio.terra.workspace.generated.model.ApiClonedControlledAzureStorageContainer;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureDiskRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureIpRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureNetworkRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureRelayNamespaceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureRelayNamespaceResult;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureStorageContainerRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureStorageRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedAzureStorageContainerSasToken;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureDisk;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureIp;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureNetwork;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureStorage;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureStorageContainer;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureVmResult;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceResult;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.SasPermissionsHelper;
import bio.terra.workspace.service.resource.controlled.cloud.azure.SasTokenOptions;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.ClonedAzureStorageContainer;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.common.annotations.VisibleForTesting;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ControlledAzureResourceApiController extends ControlledResourceControllerBase
    implements ControlledAzureResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledAzureResourceApiController.class);

  private final ControlledResourceService controlledResourceService;
  private final AzureStorageAccessService azureControlledStorageResourceService;
  private final JobService jobService;
  private final JobApiUtils jobApiUtils;
  private final FeatureConfiguration features;
  private final AzureConfiguration azureConfiguration;
  private final WorkspaceService workspaceService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;

  @Autowired
  public ControlledAzureResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledResourceService controlledResourceService,
      AzureStorageAccessService azureControlledStorageResourceService,
      SamService samService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      HttpServletRequest request,
      FeatureConfiguration features,
      AzureConfiguration azureConfiguration,
      WorkspaceService workspaceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.controlledResourceService = controlledResourceService;
    this.azureControlledStorageResourceService = azureControlledStorageResourceService;
    this.jobService = jobService;
    this.jobApiUtils = jobApiUtils;
    this.features = features;
    this.azureConfiguration = azureConfiguration;
    this.workspaceService = workspaceService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureDisk> createAzureDisk(
      UUID workspaceUuid, ApiCreateControlledAzureDiskRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            body.getAzureDisk().getRegion(),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_DISK);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .common(commonFields)
            .diskName(body.getAzureDisk().getName())
            .region(body.getAzureDisk().getRegion())
            .size(body.getAzureDisk().getSize())
            .build();

    // TODO: make createDisk call async once we have things working e2e
    final ControlledAzureDiskResource createdDisk =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAzureDisk())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK);

    var response =
        new ApiCreatedControlledAzureDisk()
            .resourceId(createdDisk.getResourceId())
            .azureDisk(createdDisk.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureIp> createAzureIp(
      UUID workspaceUuid, @Valid ApiCreateControlledAzureIpRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            body.getAzureIp().getRegion(),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_IP);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .common(commonFields)
            .ipName(body.getAzureIp().getName())
            .region(body.getAzureIp().getRegion())
            .build();

    final ControlledAzureIpResource createdIp =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAzureIp())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_IP);

    var response =
        new ApiCreatedControlledAzureIp()
            .resourceId(createdIp.getResourceId())
            .azureIp(createdIp.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreateControlledAzureRelayNamespaceResult> createAzureRelayNamespace(
      UUID workspaceUuid, @Valid ApiCreateControlledAzureRelayNamespaceRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            body.getAzureRelayNamespace().getRegion(),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    ControlledAzureRelayNamespaceResource resource =
        ControlledAzureRelayNamespaceResource.builder()
            .common(commonFields)
            .namespaceName(body.getAzureRelayNamespace().getNamespaceName())
            .region(body.getAzureRelayNamespace().getRegion())
            .build();

    final String jobId =
        controlledResourceService.createAzureRelayNamespace(
            resource,
            body.getAzureRelayNamespace(),
            commonFields.getIamRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest);

    final ApiCreateControlledAzureRelayNamespaceResult result =
        fetchCreateControlledAzureRelayNamespaceResult(jobId);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreateControlledAzureRelayNamespaceResult>
      getCreateAzureRelayNamespaceResult(UUID workspaceUuid, String jobId) throws ApiException {
    features.azureEnabledCheck();
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreateControlledAzureRelayNamespaceResult result =
        fetchCreateControlledAzureRelayNamespaceResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiCreatedAzureStorageContainerSasToken>
      createAzureStorageContainerSasToken(
          UUID workspaceUuid,
          UUID storageContainerUuid,
          String sasIpRange,
          Long sasExpirationDuration,
          String sasPermissions,
          String sasBlobName) {
    features.azureEnabledCheck();

    ControllerValidationUtils.validateIpAddressRange(sasIpRange);
    ControllerValidationUtils.validateSasExpirationDuration(
        sasExpirationDuration, azureConfiguration.getSasTokenExpiryTimeMaximumMinutesOffset());
    ControllerValidationUtils.validateSasBlobName(sasBlobName);
    SasPermissionsHelper.validateSasPermissionString(sasPermissions);

    OffsetDateTime startTime =
        OffsetDateTime.now().minusMinutes(azureConfiguration.getSasTokenStartTimeMinutesOffset());
    long secondDuration =
        sasExpirationDuration != null
            ? sasExpirationDuration
            : azureConfiguration.getSasTokenExpiryTimeMinutesOffset() * 60;
    OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(secondDuration);

    var sasBundle =
        azureControlledStorageResourceService.createAzureStorageContainerSasToken(
            workspaceUuid,
            storageContainerUuid,
            getAuthenticatedInfo(),
            new SasTokenOptions(sasIpRange, startTime, expiryTime, sasBlobName, sasPermissions));

    return new ResponseEntity<>(
        new ApiCreatedAzureStorageContainerSasToken()
            .token(sasBundle.sasToken())
            .url(sasBundle.sasUrl()),
        HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureStorage> createAzureStorage(
      UUID workspaceUuid, @Valid ApiCreateControlledAzureStorageRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            body.getAzureStorage().getRegion(),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    ControlledAzureStorageResource resource =
        ControlledAzureStorageResource.builder()
            .common(commonFields)
            .storageAccountName(body.getAzureStorage().getStorageAccountName())
            .region(body.getAzureStorage().getRegion())
            .build();

    final ControlledAzureStorageResource createdStorage =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAzureStorage())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);
    var response =
        new ApiCreatedControlledAzureStorage()
            .resourceId(createdStorage.getResourceId())
            .azureStorage(createdStorage.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureStorageContainer> createAzureStorageContainer(
      UUID workspaceUuid, @Valid ApiCreateControlledAzureStorageContainerRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            // azure storage container's region is determined by the storage account id associated
            // with it. The storage account maybe specified by the user in the creation parameters
            // or we will use the shared storage account from the landing zone. Therefore, the
            // region will be set later during the storage container creation flight.
            /*region=*/ null,
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    ControlledAzureStorageContainerResource resource =
        ControlledAzureStorageContainerResource.builder()
            .common(commonFields)
            .storageAccountId(body.getAzureStorageContainer().getStorageAccountId())
            .storageContainerName(body.getAzureStorageContainer().getStorageContainerName())
            .build();

    final ControlledAzureStorageContainerResource createdStorageContainer =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAzureStorageContainer())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
    var response =
        new ApiCreatedControlledAzureStorageContainer()
            .resourceId(createdStorageContainer.getResourceId())
            .azureStorageContainer(createdStorageContainer.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> createAzureVm(
      UUID workspaceUuid, @Valid ApiCreateControlledAzureVmRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            body.getAzureVm().getRegion(),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_VM);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    ResourceValidationUtils.validateApiAzureVmCreationParameters(body.getAzureVm());
    ControlledAzureVmResource resource =
        buildControlledAzureVmResource(body.getAzureVm(), commonFields);

    final String jobId =
        controlledResourceService.createAzureVm(
            resource,
            body.getAzureVm(),
            commonFields.getIamRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest);

    final ApiCreatedControlledAzureVmResult result = fetchCreateControlledAzureVmResult(jobId);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @VisibleForTesting
  ControlledAzureVmResource buildControlledAzureVmResource(
      ApiAzureVmCreationParameters creationParameters, ControlledResourceFields commonFields) {
    return ControlledAzureVmResource.builder()
        .common(commonFields)
        .vmName(creationParameters.getName())
        .region(creationParameters.getRegion())
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureVmUtils.getImageData(creationParameters.getVmImage()))
        .ipId(creationParameters.getIpId())
        .networkId(creationParameters.getNetworkId())
        .diskId(creationParameters.getDiskId())
        .build();
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> getCreateAzureVmResult(
      UUID workspaceUuid, String jobId) throws ApiException {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreatedControlledAzureVmResult result = fetchCreateControlledAzureVmResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureNetwork> createAzureNetwork(
      UUID workspaceUuid, ApiCreateControlledAzureNetworkRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            body.getAzureNetwork().getRegion(),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_NETWORK);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));

    ControlledAzureNetworkResource resource =
        ControlledAzureNetworkResource.builder()
            .common(commonFields)
            .networkName(body.getAzureNetwork().getName())
            .subnetName(body.getAzureNetwork().getSubnetName())
            .addressSpaceCidr(body.getAzureNetwork().getAddressSpaceCidr())
            .subnetAddressCidr(body.getAzureNetwork().getSubnetAddressCidr())
            .region(body.getAzureNetwork().getRegion())
            .build();

    final ControlledAzureNetworkResource createdNetwork =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAzureNetwork())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_NETWORK);
    var response =
        new ApiCreatedControlledAzureNetwork()
            .resourceId(createdNetwork.getResourceId())
            .azureNetwork(createdNetwork.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureStorageContainer(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceId, resourceId, body, "Azure Storage Container");
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureIp(
      UUID workspaceUuid, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceId, body, "Azure IP");
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureRelayNamespace(
      UUID workspaceUuid, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceId, body, "Azure Relay Namespace");
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureDisk(
      UUID workspaceUuid, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceId, body, "Azure Disk");
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureVm(
      UUID workspaceUuid, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceId, body, "Azure VM");
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureNetwork(
      UUID workspaceUuid, UUID resourceId, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceId, body, "Azure Networks");
  }

  @Override
  public ResponseEntity<ApiAzureIpResource> getAzureIp(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureIpResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_IP);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureRelayNamespaceResource> getAzureRelayNamespace(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureRelayNamespaceResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceId, resourceId, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureDiskResource> getAzureDisk(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureDiskResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureVmResource> getAzureVm(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureVmResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_VM);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureNetworkResource> getAzureNetwork(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureNetworkResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_NETWORK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureDiskResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getJobDeleteResult(jobId);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureIpResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getJobDeleteResult(jobId);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureVmResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getJobDeleteResult(jobId);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureNetworkResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getJobDeleteResult(jobId);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureRelayNamespaceResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getJobDeleteResult(jobId);
  }

  private ResponseEntity<ApiDeleteControlledAzureResourceResult> getJobDeleteResult(String jobId) {
    final JobApiUtils.AsyncJobResult<Void> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, Void.class);
    var response =
        new ApiDeleteControlledAzureResourceResult()
            .jobReport(jobResult.getJobReport())
            .errorReport(jobResult.getApiErrorReport());
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  private ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteHelper(
      UUID workspaceUuid,
      UUID resourceId,
      @Valid ApiDeleteControlledAzureResourceRequest body,
      String resourceName) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceId, SamControlledResourceActions.DELETE_ACTION);
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "delete {}({}) from workspace {}",
        resourceName,
        resourceId.toString(),
        workspaceUuid.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceUuid,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    return getJobDeleteResult(jobId);
  }

  private ApiCreatedControlledAzureVmResult fetchCreateControlledAzureVmResult(String jobId) {
    final JobApiUtils.AsyncJobResult<ControlledAzureVmResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAzureVmResource.class);

    ApiAzureVmResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      ControlledAzureVmResource resource = jobResult.getResult();
      apiResource = resource.toApiResource();
    }
    return new ApiCreatedControlledAzureVmResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .azureVm(apiResource);
  }

  private ApiCreateControlledAzureRelayNamespaceResult
      fetchCreateControlledAzureRelayNamespaceResult(String jobId) {
    final JobApiUtils.AsyncJobResult<ControlledAzureRelayNamespaceResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAzureRelayNamespaceResource.class);

    ApiAzureRelayNamespaceResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      ControlledAzureRelayNamespaceResource resource = jobResult.getResult();
      apiResource = resource.toApiResource();
    }
    return new ApiCreateControlledAzureRelayNamespaceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .azureRelayNameSpace(apiResource);
  }

  public ResponseEntity<ApiCloneControlledAzureStorageContainerResult> cloneAzureStorageContainer(
      UUID workspaceId, UUID resourceId, ApiCloneControlledAzureStorageContainerRequest body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateCloneAction(
        userRequest, workspaceId, body.getDestinationWorkspaceId(), resourceId);
    if (body.getCloningInstructions() == ApiCloningInstructionsEnum.REFERENCE) {
      throw new ValidationException(
          "Copying azure storage containers by reference is not supported");
    }

    var jobId =
        controlledResourceService.cloneAzureContainer(
            workspaceId,
            resourceId,
            body.getDestinationWorkspaceId(),
            UUID.randomUUID(),
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getName(),
            body.getCloningInstructions());

    final ApiCloneControlledAzureStorageContainerResult result =
        fetchCloneAzureContainerResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  public ResponseEntity<ApiCloneControlledAzureStorageContainerResult>
      getCloneAzureStorageContainerResult(UUID workspaceId, String jobId) {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    ApiCloneControlledAzureStorageContainerResult result = fetchCloneAzureContainerResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCloneControlledAzureStorageContainerResult fetchCloneAzureContainerResult(
      String jobId) {
    JobApiUtils.AsyncJobResult<ClonedAzureStorageContainer> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ClonedAzureStorageContainer.class);

    ApiClonedControlledAzureStorageContainer containerResult = null;
    if (jobResult.getResult() != null) {
      var container =
          new ApiCreatedControlledAzureStorageContainer()
              .azureStorageContainer(jobResult.getResult().storageContainer().toApiResource());
      containerResult =
          new ApiClonedControlledAzureStorageContainer()
              .effectiveCloningInstructions(
                  jobResult.getResult().effectiveCloningInstructions().toApiModel())
              .storageContainer(container)
              .sourceWorkspaceId(jobResult.getResult().sourceWorkspaceId())
              .sourceResourceId(jobResult.getResult().sourceResourceId());
    }
    return new ApiCloneControlledAzureStorageContainerResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .container(containerResult);
  }
}
