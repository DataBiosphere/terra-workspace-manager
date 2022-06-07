package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.AzureVmUtils;
import bio.terra.workspace.generated.controller.ControlledAzureResourceApi;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.http.HttpClient;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccountKey;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.sas.SasProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
public class ControlledAzureResourceApiController extends ControlledResourceControllerBase
    implements ControlledAzureResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledGcpResourceApiController.class);

  private final ControlledResourceService controlledResourceService;
  private final JobService jobService;
  private final AzureCloudContextService azureCloudContextService;
  private final CrlService crlService;
  private final AzureConfiguration azureConfig;
  private final FeatureConfiguration features;

  @Autowired
  public ControlledAzureResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledResourceService controlledResourceService,
      SamService samService,
      JobService jobService,
      AzureCloudContextService azureCloudContextService,
      CrlService crlService,
      AzureConfiguration azureConfig,
      HttpServletRequest request,
      FeatureConfiguration features) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.controlledResourceService = controlledResourceService;
    this.jobService = jobService;
    this.azureCloudContextService = azureCloudContextService;
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.features = features;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureDisk> createAzureDisk(
      UUID workspaceUuid, ApiCreateControlledAzureDiskRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);

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
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);

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
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);

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
        fetchCreateControlledAzureRelayNamespaceResult(jobId, userRequest);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreateControlledAzureRelayNamespaceResult>
      getCreateAzureRelayNamespaceResult(UUID workspaceUuid, String jobId) throws ApiException {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCreateControlledAzureRelayNamespaceResult result =
        fetchCreateControlledAzureRelayNamespaceResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiAzureRelayNamespaceResource> getAzureRelayNamespace(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureRelayNamespaceResource resource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedAzureStorageContainerSasToken>
      createAzureStorageContainerSasToken(UUID workspaceUuid, UUID storageContainerUuid) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    AzureCloudContext azureCloudContext =
        azureCloudContextService.getRequiredAzureCloudContext(workspaceUuid);
    final String userEmail =
        SamRethrow.onInterrupted(
            () -> getSamService().getUserEmailFromSam(userRequest), "getUserEmailFromSam");

    logger.info(
        "user {} requesting SAS token for Azure storage container {} in workspace {}",
        userEmail,
        storageContainerUuid.toString(),
        workspaceUuid.toString());

    final List<String> containerActions =
        SamRethrow.onInterrupted(
            () ->
                getSamService()
                    .listResourceActions(
                        userRequest,
                        SamConstants.SamResource.CONTROLLED_USER_SHARED,
                        storageContainerUuid.toString()),
            "listResourceActions");

    StringBuilder tokenPermissions = new StringBuilder();
    for (String action : containerActions) {
      if (action.equals(SamConstants.SamControlledResourceActions.READ_ACTION)) {
        tokenPermissions.append("rl");
      } else if (action.equals(SamConstants.SamControlledResourceActions.WRITE_ACTION)) {
        tokenPermissions.append("acwd");
      }
    }

    if (tokenPermissions.isEmpty()) {
      throw new ForbiddenException(
          String.format(
              "User %s is not authorized on resource %s of type %s",
              userRequest.getEmail(),
              storageContainerUuid.toString(),
              SamConstants.SamResource.CONTROLLED_USER_SHARED));
    }

    BlobContainerSasPermission blobContainerSasPermission =
        BlobContainerSasPermission.parse(tokenPermissions.toString());

    final ControlledAzureStorageContainerResource storageContainerResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, storageContainerUuid, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
    final ControlledAzureStorageResource storageAccountResource =
        controlledResourceService
            .getControlledResource(
                workspaceUuid, storageContainerResource.getStorageAccountId(), userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

    String storageAccountName = storageAccountResource.getStorageAccountName();
    String storageContainerName = storageContainerResource.getStorageContainerName();

    StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);
    StorageAccount storageAccount =
        storageManager
            .storageAccounts()
            .getByResourceGroup(azureCloudContext.getAzureResourceGroupId(), storageAccountName);
    // todo: can this be improved? get(0) makes me sad
    StorageAccountKey key = storageAccount.getKeys().get(0);
    var storageKey = new StorageSharedKeyCredential(storageAccountName, key.value());

    String endpoint =
        String.format(Locale.ROOT, "https://%s.blob.core.windows.net", storageAccountName);
    OffsetDateTime expiryTime = OffsetDateTime.now().plusHours(1);
    OffsetDateTime startTime = OffsetDateTime.now().minusMinutes(15);
    BlobContainerClient blobContainerClient =
        new BlobContainerClientBuilder()
            .credential(storageKey)
            .endpoint(endpoint)
            .httpClient(HttpClient.createDefault())
            .containerName(storageContainerName)
            .buildClient();
    BlobServiceSasSignatureValues sasValues =
        new BlobServiceSasSignatureValues(expiryTime, blobContainerSasPermission)
            .setStartTime(startTime)
            .setProtocol(SasProtocol.HTTPS_ONLY);
    String sas = blobContainerClient.generateSas(sasValues);
    logger.info(
        "SAS token with expiry time of {} generated for user {} on container {} in workspace {}",
        expiryTime.toString(),
        userEmail,
        storageContainerUuid.toString(),
        workspaceUuid.toString());

    return new ResponseEntity<>(
        new ApiCreatedAzureStorageContainerSasToken().token(sas), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureStorage> createAzureStorage(
      UUID workspaceUuid, @Valid ApiCreateControlledAzureStorageRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);

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
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);

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
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);

    ResourceValidationUtils.validateApiAzureVmCreationParameters(body.getAzureVm());
    ControlledAzureVmResource resource =
        ControlledAzureVmResource.builder()
            .common(commonFields)
            .vmName(body.getAzureVm().getName())
            .region(body.getAzureVm().getRegion())
            .vmSize(body.getAzureVm().getVmSize())
            .vmImage(AzureVmUtils.getImageData(body.getAzureVm().getVmImage()))
            .ipId(body.getAzureVm().getIpId())
            .networkId(body.getAzureVm().getNetworkId())
            .diskId(body.getAzureVm().getDiskId())
            .build();

    final String jobId =
        controlledResourceService.createAzureVm(
            resource,
            body.getAzureVm(),
            commonFields.getIamRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest);

    final ApiCreatedControlledAzureVmResult result =
        fetchCreateControlledAzureVmResult(jobId, userRequest);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> getCreateAzureVmResult(
      UUID workspaceUuid, String jobId) throws ApiException {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCreatedControlledAzureVmResult result =
        fetchCreateControlledAzureVmResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureNetwork> createAzureNetwork(
      UUID workspaceUuid, ApiCreateControlledAzureNetworkRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);

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
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_IP);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureDiskResource> getAzureDisk(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureDiskResource resource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureVmResource> getAzureVm(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureVmResource resource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_VM);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureNetworkResource> getAzureNetwork(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureNetworkResource resource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_NETWORK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureDiskResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureIpResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureVmResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureNetworkResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getJobDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureRelayNamespaceResult(
      UUID workspaceUuid, String jobId) {
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

  private ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteHelper(
      UUID workspaceUuid,
      UUID resourceId,
      @Valid ApiDeleteControlledAzureResourceRequest body,
      String resourceName) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
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
            userRequest,
            true);
    return getJobDeleteResult(jobId, userRequest);
  }

  private ApiCreatedControlledAzureVmResult fetchCreateControlledAzureVmResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    final JobService.AsyncJobResult<ControlledAzureVmResource> jobResult =
        jobService.retrieveAsyncJobResult(jobId, ControlledAzureVmResource.class, userRequest);

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
      fetchCreateControlledAzureRelayNamespaceResult(
          String jobId, AuthenticatedUserRequest userRequest) {
    final JobService.AsyncJobResult<ControlledAzureRelayNamespaceResource> jobResult =
        jobService.retrieveAsyncJobResult(
            jobId, ControlledAzureRelayNamespaceResource.class, userRequest);

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
}
