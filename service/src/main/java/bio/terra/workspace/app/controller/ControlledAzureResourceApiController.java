package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.AzureUtils.parseAccountNameFromUserAssignedManagedIdentity;
import static bio.terra.workspace.common.utils.MapperUtils.BatchPoolMapper.mapFrom;
import static bio.terra.workspace.common.utils.MapperUtils.BatchPoolMapper.mapListOfApplicationPackageReferences;
import static bio.terra.workspace.common.utils.MapperUtils.BatchPoolMapper.mapListOfMetadataItems;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.ValidationException;
import bio.terra.common.iam.BearerToken;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledAzureResourceApi;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.AzureResourceValidationUtils;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.SasPermissionsHelper;
import bio.terra.workspace.service.resource.controlled.cloud.azure.SasTokenOptions;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.model.BatchPoolUserAssignedManagedIdentity;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace.ControlledAzureKubernetesNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
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

  private final AzureConfiguration azureConfiguration;
  private final AzureStorageAccessService azureControlledStorageResourceService;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final WsmResourceService wsmResourceService;

  @Autowired
  public ControlledAzureResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration featureConfiguration,
      FeatureService featureService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      ControlledResourceService controlledResourceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      WorkspaceService workspaceService,
      AzureConfiguration azureConfiguration,
      AzureStorageAccessService azureControlledStorageResourceService,
      LandingZoneApiDispatch landingZoneApiDispatch,
      WsmResourceService wsmResourceService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        featureConfiguration,
        featureService,
        jobService,
        jobApiUtils,
        controlledResourceService,
        controlledResourceMetadataManager,
        workspaceService);
    this.azureConfiguration = azureConfiguration;
    this.azureControlledStorageResourceService = azureControlledStorageResourceService;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.wsmResourceService = wsmResourceService;
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledAzureDisk> createAzureDisk(
      UUID workspaceUuid, ApiCreateControlledAzureDiskRequestBody body) {
    features.azureEnabledCheck();

    // validate workspace access
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        validateWorkspaceResourceCreationPermissions(userRequest, workspaceUuid, body.getCommon());

    // create the resource
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegionForWorkspaceUsingWsmToken(workspace),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_DISK);

    ControlledAzureDiskResource resource =
        buildControlledAzureDiskResource(body.getAzureDisk(), commonFields);

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

  @WithSpan
  @Override
  public ResponseEntity<ApiCreateControlledAzureResourceResult> createAzureDiskV2(
      UUID workspaceUuid, ApiCreateControlledAzureDiskRequestV2Body body) {
    features.azureEnabledCheck();

    // validate workspace access
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        validateWorkspaceResourceCreationPermissions(userRequest, workspaceUuid, body.getCommon());

    // create the resource
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegionForWorkspaceUsingWsmToken(workspace),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_DISK);

    ControlledAzureDiskResource resource =
        buildControlledAzureDiskResource(body.getAzureDisk(), commonFields);

    final String jobId =
        controlledResourceService.createControlledResourceAsync(
            resource,
            commonFields.getIamRole(),
            userRequest,
            body.getAzureDisk(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"));

    final ApiCreateControlledAzureResourceResult result =
        fetchCreateControlledAzureDiskResult(jobId);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreateControlledAzureResourceResult> getCreateAzureDiskResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreateControlledAzureResourceResult result = fetchCreateControlledAzureDiskResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
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
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // You must have at least READ on the workspace to use this method. Actual permissions
    // are determined below.
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.AZURE);

    ControllerValidationUtils.validateIpAddressRange(sasIpRange);
    AzureUtils.validateSasExpirationDuration(
        sasExpirationDuration, azureConfiguration.getSasTokenExpiryTimeMaximumMinutesOffset());
    AzureUtils.validateSasBlobName(sasBlobName);
    SasPermissionsHelper.validateSasPermissionString(sasPermissions);

    OffsetDateTime startTime =
        OffsetDateTime.now().minusMinutes(azureConfiguration.getSasTokenStartTimeMinutesOffset());
    long secondDuration =
        sasExpirationDuration != null
            ? sasExpirationDuration
            : azureConfiguration.getSasTokenExpiryTimeMinutesOffset() * 60;
    OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(secondDuration);

    // TODO: the access control is buried inside of AzureStorageAccessService. It should be
    //  here in the controller with the results passed into the method.
    var sasBundle =
        azureControlledStorageResourceService.createAzureStorageContainerSasToken(
            workspaceUuid,
            storageContainerUuid,
            userRequest,
            new SasTokenOptions(sasIpRange, startTime, expiryTime, sasBlobName, sasPermissions));

    return new ResponseEntity<>(
        new ApiCreatedAzureStorageContainerSasToken()
            .token(sasBundle.sasToken())
            .url(sasBundle.sasUrl()),
        HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledAzureStorageContainer> createAzureStorageContainer(
      UUID workspaceUuid, ApiCreateControlledAzureStorageContainerRequestBody body) {
    features.azureEnabledCheck();

    // validate the workspace and user access
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        validateWorkspaceResourceCreationPermissions(userRequest, workspaceUuid, body.getCommon());

    // create the resource
    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegionForWorkspaceUsingWsmToken(workspace),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);

    ControlledAzureStorageContainerResource resource =
        buildControlledAzureStorageContainerResource(body.getAzureStorageContainer(), commonFields);

    final ControlledAzureStorageContainerResource createdStorageContainer =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAzureStorageContainer())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
    UUID resourceUuid = createdStorageContainer.getResourceId();
    var response =
        new ApiCreatedControlledAzureStorageContainer()
            .resourceId(resourceUuid)
            .azureStorageContainer(createdStorageContainer.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> createAzureVm(
      UUID workspaceUuid, ApiCreateControlledAzureVmRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        validateWorkspaceResourceCreationPermissions(userRequest, workspaceUuid, body.getCommon());

    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegionForWorkspaceUsingWsmToken(workspace),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_VM);

    AzureResourceValidationUtils.validate(body.getAzureVm());
    ControlledAzureVmResource resource =
        buildControlledAzureVmResource(body.getAzureVm(), commonFields);

    final String jobId =
        controlledResourceService.createControlledResourceAsync(
            resource,
            commonFields.getIamRole(),
            userRequest,
            body.getAzureVm(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"));

    final ApiCreatedControlledAzureVmResult result = fetchCreateControlledAzureVmResult(jobId);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @VisibleForTesting
  ControlledAzureVmResource buildControlledAzureVmResource(
      ApiAzureVmCreationParameters creationParameters, ControlledResourceFields commonFields) {
    return ControlledAzureVmResource.builder()
        .common(commonFields)
        .vmName(creationParameters.getName())
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
        .diskId(creationParameters.getDiskId())
        .build();
  }

  @VisibleForTesting
  ControlledAzureDiskResource buildControlledAzureDiskResource(
      ApiAzureDiskCreationParameters creationParameters, ControlledResourceFields commonFields) {
    return ControlledAzureDiskResource.builder()
        .common(commonFields)
        .diskName(creationParameters.getName())
        .size(creationParameters.getSize())
        .build();
  }

  @VisibleForTesting
  ControlledAzureDatabaseResource buildControlledAzureDatabaseResource(
      ApiAzureDatabaseCreationParameters creationParameters,
      ControlledResourceFields commonFields) {
    return ControlledAzureDatabaseResource.builder()
        .common(commonFields)
        .databaseOwner(
            maybeLookupName(commonFields.getWorkspaceId(), creationParameters.getOwner()))
        .databaseName(creationParameters.getName())
        .allowAccessForAllWorkspaceUsers(creationParameters.isAllowAccessForAllWorkspaceUsers())
        .build();
  }

  @VisibleForTesting
  ControlledAzureStorageContainerResource buildControlledAzureStorageContainerResource(
      ApiAzureStorageContainerCreationParameters creationParameters,
      ControlledResourceFields commonFields) {
    return ControlledAzureStorageContainerResource.builder()
        .common(commonFields)
        .storageContainerName(creationParameters.getStorageContainerName())
        .build();
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> getCreateAzureVmResult(
      UUID workspaceUuid, String jobId) throws ApiException {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreatedControlledAzureVmResult result = fetchCreateControlledAzureVmResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledAzureBatchPool> createAzureBatchPool(
      UUID workspaceUuid, ApiCreateControlledAzureBatchPoolRequestBody body) {
    features.azureEnabledCheck();

    // validate the workspace and user access
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        validateWorkspaceResourceCreationPermissions(userRequest, workspaceUuid, body.getCommon());
    String userEmail = samService.getSamUser(userRequest).getEmail();

    AzureCloudContext cloudContext = workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AZURE).castByEnum(CloudPlatform.AZURE);

      String userManagedIdentity = null;
      try {
          userManagedIdentity = samService.getOrCreateUserManagedIdentityForUser(userEmail, cloudContext.getAzureSubscriptionId(), cloudContext.getAzureTenantId(), cloudContext.getAzureResourceGroupId());
      } catch (InterruptedException e) {
          throw new RuntimeException(e);
      }
      String petName = parseAccountNameFromUserAssignedManagedIdentity(userManagedIdentity);

    BatchPoolUserAssignedManagedIdentity azureUserAssignedManagedIdentity = new BatchPoolUserAssignedManagedIdentity(
            cloudContext.getAzureResourceGroupId(), petName, null);

    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegionForWorkspaceUsingWsmToken(workspace),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_BATCH_POOL);

    ControlledAzureBatchPoolResource resource =
        ControlledAzureBatchPoolResource.builder()
            .common(commonFields)
            .id(body.getAzureBatchPool().getId())
            .vmSize(body.getAzureBatchPool().getVmSize())
            .displayName(body.getAzureBatchPool().getDisplayName())
            .deploymentConfiguration(mapFrom(body.getAzureBatchPool().getDeploymentConfiguration()))
            .userAssignedIdentities(
                List.of(azureUserAssignedManagedIdentity))
            .scaleSettings(mapFrom(body.getAzureBatchPool().getScaleSettings()))
            .startTask(mapFrom(body.getAzureBatchPool().getStartTask()))
            .applicationPackages(
                mapListOfApplicationPackageReferences(
                    body.getAzureBatchPool().getApplicationPackages()))
            .networkConfiguration(mapFrom(body.getAzureBatchPool().getNetworkConfiguration()))
            .metadata(mapListOfMetadataItems(body.getAzureBatchPool().getMetadata()))
            .build();

    final ControlledAzureBatchPoolResource createdBatchPool =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAzureBatchPool())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_BATCH_POOL);

    var response =
        new ApiCreatedControlledAzureBatchPool()
            .resourceId(createdBatchPool.getResourceId())
            .azureBatchPool(createdBatchPool.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<Void> deleteAzureBatchPool(UUID workspaceUuid, UUID resourceUuid) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AZURE);

    logger.info(
        "delete {}({}) from workspace {}",
        "Azure Batch Pool",
        resourceUuid.toString(),
        workspaceUuid.toString());

    controlledResourceService.deleteControlledResourceSync(
        workspaceUuid, resourceUuid, /* forceDelete= */ false, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureStorageContainer(
      UUID workspaceUuid, UUID resourceUuid, ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceUuid, body, "Azure Storage Container");
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureDisk(
      UUID workspaceUuid, UUID resourceUuid, ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceUuid, body, "Azure Disk");
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureVm(
      UUID workspaceUuid, UUID resourceUuid, ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceUuid, body, "Azure VM");
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAzureDiskResource> getAzureDisk(UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureDiskResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAzureVmResource> getAzureVm(UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureVmResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_VM);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureDiskResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getJobDeleteResult(jobId);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureVmResult(
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
      UUID resourceUuid,
      ApiDeleteControlledAzureResourceRequest body,
      String resourceName) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AZURE);
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "delete {}({}) from workspace {}",
        resourceName,
        resourceUuid.toString(),
        workspaceUuid.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl.getId(),
            workspaceUuid,
            resourceUuid,
            /* forceDelete= */ false,
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

  private ApiCreateControlledAzureResourceResult fetchCreateControlledAzureDiskResult(
      String jobId) {
    final JobApiUtils.AsyncJobResult<ControlledAzureDiskResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAzureDiskResource.class);

    return new ApiCreateControlledAzureResourceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCloneControlledAzureStorageContainerResult> cloneAzureStorageContainer(
      UUID workspaceUuid, UUID resourceUuid, ApiCloneControlledAzureStorageContainerRequest body) {
    features.azureEnabledCheck();
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAzureStorageContainerResource resource =
        controlledResourceMetadataManager
            .validateCloneAction(
                userRequest, workspaceUuid, body.getDestinationWorkspaceId(), resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
    CloningInstructions cloningInstructions =
        resource.computeCloningInstructions(body.getCloningInstructions());
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.CONTROLLED, cloningInstructions);
    if (CloningInstructions.isReferenceClone(body.getCloningInstructions())) {
      throw new ValidationException(
          "Copying azure storage containers by reference is not supported");
    }
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AZURE);
    workspaceService.validateCloneWorkspaceAndContextState(
        body.getDestinationWorkspaceId(), CloudPlatform.AZURE, cloningInstructions);

    var jobId =
        controlledResourceService.cloneAzureContainer(
            resource,
            body.getDestinationWorkspaceId(),
            UUID.randomUUID(),
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getName(),
            cloningInstructions,
            body.getPrefixesToClone());

    ApiCloneControlledAzureStorageContainerResult result = fetchCloneAzureContainerResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCloneControlledAzureStorageContainerResult>
      getCloneAzureStorageContainerResult(UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCloneControlledAzureStorageContainerResult result = fetchCloneAzureContainerResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCloneControlledAzureStorageContainerResult fetchCloneAzureContainerResult(
      String jobId) {
    JobApiUtils.AsyncJobResult<ClonedAzureResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ClonedAzureResource.class);

    ApiClonedControlledAzureStorageContainer containerResult = null;
    if (jobResult.getResult() != null) {
      ControlledAzureStorageContainerResource containerResource =
          jobResult.getResult().storageContainer();
      var container =
          new ApiCreatedControlledAzureStorageContainer()
              .azureStorageContainer(containerResource.toApiResource());
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

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult>
      getDeleteAzureStorageContainerResult(UUID workspaceId, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    return getJobDeleteResult(jobId);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledAzureDatabaseResult> createAzureDatabase(
      UUID workspaceUuid, ApiCreateControlledAzureDatabaseRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        validateWorkspaceResourceCreationPermissions(userRequest, workspaceUuid, body.getCommon());

    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegionForWorkspaceUsingWsmToken(workspace),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_DATABASE);

    ControlledAzureDatabaseResource resource =
        buildControlledAzureDatabaseResource(body.getAzureDatabase(), commonFields);

    var jobId =
        controlledResourceService.createControlledResourceAsync(
            resource,
            commonFields.getIamRole(),
            userRequest,
            body.getAzureDatabase(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"));

    return new ResponseEntity<>(fetchCreateControlledAzureDatabaseResult(jobId), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureDatabaseResult> getCreateAzureDatabaseResult(
      UUID workspaceId, String jobId) {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    var result = fetchCreateControlledAzureDatabaseResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCreatedControlledAzureDatabaseResult fetchCreateControlledAzureDatabaseResult(
      String jobId) {
    final JobApiUtils.AsyncJobResult<ControlledAzureDatabaseResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAzureDatabaseResource.class);

    ApiAzureDatabaseResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      var resource = jobResult.getResult();
      apiResource = resource.toApiResource();
    }
    return new ApiCreatedControlledAzureDatabaseResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .azureDatabase(apiResource);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledAzureManagedIdentity> createAzureManagedIdentity(
      UUID workspaceUuid, ApiCreateControlledAzureManagedIdentityRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegionForWorkspaceUsingWsmToken(
                workspaceService.getWorkspace(workspaceUuid)),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AZURE);

    var resource =
        ControlledAzureManagedIdentityResource.builder()
            .common(commonFields)
            .managedIdentityName(body.getAzureManagedIdentity().getName())
            .build();

    final ControlledAzureManagedIdentityResource createdManagedIdentity =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getAzureManagedIdentity())
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);

    var response =
        new ApiCreatedControlledAzureManagedIdentity()
            .resourceId(createdManagedIdentity.getResourceId())
            .azureManagedIdentity(createdManagedIdentity.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<Void> deleteAzureDatabase(UUID workspaceId, UUID resourceId) {
    return deleteControlledResourceSync(workspaceId, resourceId);
  }

  @NotNull
  private ResponseEntity<Void> deleteControlledResourceSync(UUID workspaceUuid, UUID resourceUuid) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.AZURE);
    controlledResourceService.deleteControlledResourceSync(
        workspaceUuid, resourceUuid, /* forceDelete= */ false, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @WithSpan
  @Override
  public ResponseEntity<Void> deleteAzureManagedIdentity(UUID workspaceId, UUID resourceId) {
    return deleteControlledResourceSync(workspaceId, resourceId);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAzureDatabaseResource> getAzureDatabase(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureDatabaseResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(userRequest, workspaceId, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_DATABASE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiAzureManagedIdentityResource> getAzureManagedIdentity(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureManagedIdentityResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(userRequest, workspaceId, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureDatabaseAsync(
      UUID workspaceUuid, UUID resourceUuid, ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceUuid, body, "Azure Database");
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureDatabaseResult(
      UUID workspaceId, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    return getJobDeleteResult(jobId);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureKubernetesNamespaceResult>
      createAzureKubernetesNamespace(
          UUID workspaceId, ApiCreateControlledAzureKubernetesNamespaceRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        validateWorkspaceResourceCreationPermissions(userRequest, workspaceId, body.getCommon());

    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceId,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegionForWorkspaceUsingWsmToken(workspace),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_DATABASE);

    // append the workspace id to ensure the namespace is unique across all workspaces in the LZ
    var kubernetesNamespace =
        body.getAzureKubernetesNamespace().getNamespacePrefix() + "-" + workspaceId;
    var resource =
        ControlledAzureKubernetesNamespaceResource.builder()
            .common(commonFields)
            .kubernetesNamespace(kubernetesNamespace)
            .kubernetesServiceAccount(kubernetesNamespace + "-ksa")
            .managedIdentity(
                maybeLookupName(
                    workspaceId, body.getAzureKubernetesNamespace().getManagedIdentity()))
            .databases(
                body.getAzureKubernetesNamespace().getDatabases().stream()
                    .map(n -> maybeLookupName(workspaceId, n))
                    .collect(Collectors.toSet()))
            .build();

    var jobId =
        controlledResourceService.createControlledResourceAsync(
            resource,
            commonFields.getIamRole(),
            userRequest,
            body.getAzureKubernetesNamespace(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"));

    return new ResponseEntity<>(
        fetchCreateControlledKubernetesNamespaceResult(jobId), HttpStatus.OK);
  }

  /**
   * Initial implementations of createAzureKubernetesNamespace and createAzureDatabase required ids
   * of the resources to be passed in. But names are easier to use and port nicely during clone
   * operations. So currently the api supports both names and ids. But we convert to names before
   * storing the resource.
   *
   * @param workspaceId
   * @param maybeUuid a uuid or a name
   * @return if maybeUuid is a uuid and exists in the workspace, return the name of the resource
   *     with that uuid. Otherwise, return maybeUuid.
   */
  private String maybeLookupName(UUID workspaceId, String maybeUuid) {
    if (maybeUuid == null) {
      return null;
    }
    try {
      return wsmResourceService.getResource(workspaceId, UUID.fromString(maybeUuid)).getName();
    } catch (IllegalArgumentException | ResourceNotFoundException e) {
      return maybeUuid;
    }
  }

  private ApiCreatedControlledAzureKubernetesNamespaceResult
      fetchCreateControlledKubernetesNamespaceResult(String jobId) {
    final JobApiUtils.AsyncJobResult<ControlledAzureKubernetesNamespaceResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAzureKubernetesNamespaceResource.class);

    ApiAzureKubernetesNamespaceResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      var resource = jobResult.getResult();
      apiResource = resource.toApiResource();
    }
    return new ApiCreatedControlledAzureKubernetesNamespaceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .azureKubernetesNamespace(apiResource);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureKubernetesNamespace(
      UUID workspaceId, UUID resourceId, ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceId, resourceId, body, "Azure Kubernetes Namespace");
  }

  @Override
  public ResponseEntity<ApiAzureKubernetesNamespaceResource> getAzureKubernetesNamespace(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureKubernetesNamespaceResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(userRequest, workspaceId, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_KUBERNETES_NAMESPACE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledAzureKubernetesNamespaceResult>
      getCreateAzureKubernetesNamespaceResult(UUID workspaceId, String jobId) {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    var result = fetchCreateControlledKubernetesNamespaceResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult>
      getDeleteAzureKubernetesNamespaceResult(UUID workspaceId, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    return getJobDeleteResult(jobId);
  }

  @Override
  public ResponseEntity<ApiResourceQuota> getWorkspaceAzureLandingZoneResourceQuota(
      UUID workspaceId, String azureResourceId) {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamWorkspaceAction.WRITE);
    var wsmToken = new BearerToken(samService.getWsmServiceAccountToken());
    var landingZoneId = landingZoneApiDispatch.getLandingZoneId(wsmToken, workspace);

    var result = landingZoneApiDispatch.getResourceQuota(wsmToken, landingZoneId, azureResourceId);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneResourcesList> listWorkspaceAzureLandingZoneResources(
      UUID workspaceId) {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    var workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamWorkspaceAction.WRITE);
    var wsmToken = new BearerToken(samService.getWsmServiceAccountToken());
    var landingZoneId = landingZoneApiDispatch.getLandingZoneId(wsmToken, workspace);

    var result = landingZoneApiDispatch.listAzureLandingZoneResources(wsmToken, landingZoneId);
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  private Workspace validateWorkspaceResourceCreationPermissions(
      AuthenticatedUserRequest userRequest,
      UUID workpaceUUID,
      ApiControlledResourceCommonFields commonFields) {
    var workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest,
            workpaceUUID,
            ControllerValidationUtils.samCreateAction(
                AccessScopeType.fromApi(commonFields.getAccessScope()),
                ManagedByType.fromApi(commonFields.getManagedBy())));
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.AZURE);
    return workspace;
  }
}
