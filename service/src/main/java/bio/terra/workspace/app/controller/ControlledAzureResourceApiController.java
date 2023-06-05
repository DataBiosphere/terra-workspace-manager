package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MapperUtils.BatchPoolMapper.mapFrom;
import static bio.terra.workspace.common.utils.MapperUtils.BatchPoolMapper.mapListOfApplicationPackageReferences;
import static bio.terra.workspace.common.utils.MapperUtils.BatchPoolMapper.mapListOfMetadataItems;
import static bio.terra.workspace.common.utils.MapperUtils.BatchPoolMapper.mapListOfUserAssignedIdentities;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledAzureResourceApi;
import bio.terra.workspace.generated.model.ApiAzureDiskResource;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmResource;
import bio.terra.workspace.generated.model.ApiCloneControlledAzureStorageContainerRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledAzureStorageContainerResult;
import bio.terra.workspace.generated.model.ApiClonedControlledAzureStorageContainer;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureBatchPoolRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureDiskRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureStorageContainerRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledAzureVmRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedAzureStorageContainerSasToken;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureBatchPool;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureDisk;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureStorageContainer;
import bio.terra.workspace.generated.model.ApiCreatedControlledAzureVmResult;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledAzureResourceResult;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.SasPermissionsHelper;
import bio.terra.workspace.service.resource.controlled.cloud.azure.SasTokenOptions;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.ClonedAzureStorageContainer;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.common.annotations.VisibleForTesting;
import io.opencensus.contrib.spring.aop.Traced;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ControlledAzureResourceApiController extends ControlledResourceControllerBase
    implements ControlledAzureResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledAzureResourceApiController.class);

  private final AzureConfiguration azureConfiguration;
  private final AzureStorageAccessService azureControlledStorageResourceService;
  private final LandingZoneApiDispatch landingZoneApiDispatch;

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
      LandingZoneApiDispatch landingZoneApiDispatch) {
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
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledAzureDisk> createAzureDisk(
      UUID workspaceUuid, ApiCreateControlledAzureDiskRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegion(
                userRequest, workspaceService.getWorkspace(workspaceUuid)),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_DISK);
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.AZURE);

    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .common(commonFields)
            .diskName(body.getAzureDisk().getName())
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

  @Traced
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

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledAzureStorageContainer> createAzureStorageContainer(
      UUID workspaceUuid, @Valid ApiCreateControlledAzureStorageContainerRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegion(
                userRequest, workspaceService.getWorkspace(workspaceUuid)),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.AZURE);

    ControlledAzureStorageContainerResource resource =
        ControlledAzureStorageContainerResource.builder()
            .common(commonFields)
            .storageContainerName(body.getAzureStorageContainer().getStorageContainerName())
            .build();

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

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> createAzureVm(
      UUID workspaceUuid, @Valid ApiCreateControlledAzureVmRequestBody body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegion(
                userRequest, workspaceService.getWorkspace(workspaceUuid)),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_VM);
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.AZURE);

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
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
        .diskId(creationParameters.getDiskId())
        .build();
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledAzureVmResult> getCreateAzureVmResult(
      UUID workspaceUuid, String jobId) throws ApiException {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreatedControlledAzureVmResult result = fetchCreateControlledAzureVmResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledAzureBatchPool> createAzureBatchPool(
      UUID workspaceUuid, ApiCreateControlledAzureBatchPoolRequestBody body) {
    features.azureEnabledCheck();

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            landingZoneApiDispatch.getLandingZoneRegion(
                userRequest, workspaceService.getWorkspace(workspaceUuid)),
            userRequest,
            WsmResourceType.CONTROLLED_AZURE_BATCH_POOL);
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.AZURE);

    ControlledAzureBatchPoolResource resource =
        ControlledAzureBatchPoolResource.builder()
            .common(commonFields)
            .id(body.getAzureBatchPool().getId())
            .vmSize(body.getAzureBatchPool().getVmSize())
            .displayName(body.getAzureBatchPool().getDisplayName())
            .deploymentConfiguration(mapFrom(body.getAzureBatchPool().getDeploymentConfiguration()))
            .userAssignedIdentities(
                mapListOfUserAssignedIdentities(
                    body.getAzureBatchPool().getUserAssignedIdentities()))
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

  @Traced
  @Override
  public ResponseEntity<Void> deleteAzureBatchPool(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @PathVariable("resourceId") UUID resourceUuid) {
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
        workspaceUuid, resourceUuid, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureStorageContainer(
      UUID workspaceUuid, UUID resourceUuid, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceUuid, body, "Azure Storage Container");
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureDisk(
      UUID workspaceUuid, UUID resourceUuid, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceUuid, body, "Azure Disk");
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> deleteAzureVm(
      UUID workspaceUuid, UUID resourceUuid, @Valid ApiDeleteControlledAzureResourceRequest body) {
    return deleteHelper(workspaceUuid, resourceUuid, body, "Azure VM");
  }

  @Traced
  @Override
  public ResponseEntity<ApiAzureDiskResource> getAzureDisk(UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureDiskResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_DISK);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiAzureVmResource> getAzureVm(UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    features.azureEnabledCheck();
    final ControlledAzureVmResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_AZURE_VM);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledAzureResourceResult> getDeleteAzureDiskResult(
      UUID workspaceUuid, String jobId) {
    features.azureEnabledCheck();
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getJobDeleteResult(jobId);
  }

  @Traced
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
      @Valid ApiDeleteControlledAzureResourceRequest body,
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
            jobControl,
            workspaceUuid,
            resourceUuid,
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

  @Traced
  @Override
  public ResponseEntity<ApiCloneControlledAzureStorageContainerResult> cloneAzureStorageContainer(
      UUID workspaceUuid, UUID resourceUuid, ApiCloneControlledAzureStorageContainerRequest body) {
    features.azureEnabledCheck();

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateCloneAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId(), resourceUuid);
    if (CloningInstructions.isReferenceClone(body.getCloningInstructions())) {
      throw new ValidationException(
          "Copying azure storage containers by reference is not supported");
    }
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);
    workspaceService.validateWorkspaceAndContextState(
        body.getDestinationWorkspaceId(), CloudPlatform.GCP);

    var jobId =
        controlledResourceService.cloneAzureContainer(
            workspaceUuid,
            resourceUuid,
            body.getDestinationWorkspaceId(),
            UUID.randomUUID(),
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getName(),
            body.getCloningInstructions(),
            body.getPrefixesToClone());

    final ApiCloneControlledAzureStorageContainerResult result =
        fetchCloneAzureContainerResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Traced
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
    JobApiUtils.AsyncJobResult<ClonedAzureStorageContainer> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ClonedAzureStorageContainer.class);

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
}
