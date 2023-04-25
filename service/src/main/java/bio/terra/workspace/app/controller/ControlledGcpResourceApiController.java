package bio.terra.workspace.app.controller;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledGcpResourceApi;
import bio.terra.workspace.generated.model.ApiAiNotebookCloudId;
import bio.terra.workspace.generated.model.ApiBqDatasetCloudId;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetResult;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpAiNotebookInstanceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcsBucketCloudName;
import bio.terra.workspace.generated.model.ApiGenerateGcpAiNotebookCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpBigQueryDatasetCloudIDRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpGcsBucketCloudNameRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import com.google.common.base.Strings;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class ControlledGcpResourceApiController extends ControlledResourceControllerBase
    implements ControlledGcpResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledGcpResourceApiController.class);

  private final WorkspaceService workspaceService;
  private final WsmResourceService wsmResourceService;
  private final GcpCloudContextService gcpCloudContextService;

  @Autowired
  public ControlledGcpResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      JobService jobService,
      JobApiUtils jobApiUtils,
      ControlledResourceService controlledResourceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      WorkspaceService workspaceService,
      WsmResourceService wsmResourceService,
      GcpCloudContextService gcpCloudContextService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        features,
        jobService,
        jobApiUtils,
        controlledResourceService,
        controlledResourceMetadataManager);
    this.workspaceService = workspaceService;
    this.wsmResourceService = wsmResourceService;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledGcpGcsBucket> createBucket(
      UUID workspaceUuid, @Valid ApiCreateControlledGcpGcsBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    String resourceLocation = getResourceLocation(workspace, body.getGcsBucket().getLocation());
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            resourceLocation,
            userRequest,
            WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    ControlledGcsBucketResource resource =
        ControlledGcsBucketResource.builder()
            .bucketName(
                StringUtils.isEmpty(body.getGcsBucket().getName())
                    ? ControlledGcsBucketHandler.getHandler()
                        .generateCloudName(commonFields.getWorkspaceId(), commonFields.getName())
                    : body.getGcsBucket().getName())
            .common(commonFields)
            .build();

    body.getGcsBucket().location(resourceLocation);

    final ControlledGcsBucketResource createdBucket =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getGcsBucket())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    var response =
        new ApiCreatedControlledGcpGcsBucket()
            .resourceId(createdBucket.getResourceId())
            .gcpBucket(createdBucket.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private String getResourceLocation(Workspace workspace, String requestedLocation) {
    return Strings.isNullOrEmpty(requestedLocation)
        ? workspace
            .getProperties()
            .getOrDefault(
                WorkspaceConstants.Properties.DEFAULT_RESOURCE_LOCATION,
                GcpResourceConstant.DEFAULT_REGION)
        : requestedLocation;
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> deleteBucket(
      UUID workspaceUuid, UUID resourceId, @Valid ApiDeleteControlledGcpGcsBucketRequest body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceId, SamControlledResourceActions.DELETE_ACTION);
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteBucket workspace {} resource {}", workspaceUuid.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceUuid,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    return getDeleteResult(jobId);
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> getDeleteBucketResult(
      UUID workspaceUuid, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getDeleteResult(jobId);
  }

  private ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> getDeleteResult(String jobId) {
    JobApiUtils.AsyncJobResult<Void> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, Void.class);
    var response =
        new ApiDeleteControlledGcpGcsBucketResult()
            .jobReport(jobResult.getJobReport())
            .errorReport(jobResult.getApiErrorReport());
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucket(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGcsBucketResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcsBucketCloudName> generateGcpGcsBucketCloudName(
      UUID workspaceUuid, ApiGenerateGcpGcsBucketCloudNameRequestBody name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    String generatedBucketName =
        ControlledGcsBucketHandler.getHandler()
            .generateCloudName(workspaceUuid, name.getGcsBucketName());
    var response = new ApiGcsBucketCloudName().generatedBucketCloudName(generatedBucketName);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> updateGcsBucket(
      UUID workspaceUuid, UUID resourceId, @Valid ApiUpdateControlledGcpGcsBucketRequestBody body) {
    logger.info("Updating bucket resourceId {} workspaceUuid {}", resourceId, workspaceUuid);
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGcsBucketResource bucketResource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    ApiGcpGcsBucketUpdateParameters updateParameters = body.getUpdateParameters();
    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(
                StewardshipType.CONTROLLED,
                (updateParameters == null ? null : updateParameters.getCloningInstructions()));
    wsmResourceService.updateResource(
        userRequest, bucketResource, commonUpdateParameters, updateParameters);
    ControlledGcsBucketResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> cloneGcsBucket(
      UUID workspaceUuid, UUID resourceId, @Valid ApiCloneControlledGcpGcsBucketRequest body) {
    logger.info("Cloning GCS bucket resourceId {} workspaceUuid {}", resourceId, workspaceUuid);

    if (CloningInstructions.isReferenceClone(body.getCloningInstructions())
        && (!StringUtils.isEmpty(body.getBucketName())
            || !StringUtils.isEmpty(body.getLocation()))) {
      throw new ValidationException(
          "Cannot set bucket or location when cloning a controlled bucket with COPY_REFERENCE or LINK_REFERENCE");
    }

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // This technically duplicates the first step of the flight as the clone flight is re-used for
    // cloneWorkspace, but this also saves us from launching and failing a flight if the user does
    // not have access to the resource.
    controlledResourceMetadataManager.validateCloneAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId(), resourceId);

    final String jobId =
        controlledResourceService.cloneGcsBucket(
            workspaceUuid,
            resourceId,
            body.getDestinationWorkspaceId(),
            UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getBucketName(),
            body.getLocation(),
            body.getCloningInstructions());
    final ApiCloneControlledGcpGcsBucketResult result = fetchCloneGcsBucketResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCloneControlledGcpGcsBucketResult fetchCloneGcsBucketResult(String jobId) {
    JobApiUtils.AsyncJobResult<ApiClonedControlledGcpGcsBucket> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ApiClonedControlledGcpGcsBucket.class);
    return new ApiCloneControlledGcpGcsBucketResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .bucket(jobResult.getResult());
  }

  @Traced
  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> getCloneGcsBucketResult(
      UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCloneControlledGcpGcsBucketResult result = fetchCloneGcsBucketResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDataset(
      UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledBigQueryDatasetResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiBqDatasetCloudId> generateBigQueryDatasetCloudId(
      UUID workspaceUuid, ApiGenerateGcpBigQueryDatasetCloudIDRequestBody name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    String generatedCloudBqDatasetName =
        ControlledBigQueryDatasetHandler.getHandler()
            .generateCloudName(workspaceUuid, name.getBigQueryDatasetName());
    var response = new ApiBqDatasetCloudId().generatedDatasetCloudId(generatedCloudBqDatasetName);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> updateBigQueryDataset(
      UUID workspaceUuid, UUID resourceId, ApiUpdateControlledGcpBigQueryDatasetRequestBody body) {
    logger.info("Updating dataset resourceId {} workspaceUuid {}", resourceId, workspaceUuid);
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledBigQueryDatasetResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    ApiGcpBigQueryDatasetUpdateParameters updateParameters = body.getUpdateParameters();
    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters()
            .setName(body.getName())
            .setDescription(body.getDescription())
            .setCloningInstructions(
                StewardshipType.CONTROLLED,
                (updateParameters == null ? null : updateParameters.getCloningInstructions()));
    wsmResourceService.updateResource(
        userRequest, resource, commonUpdateParameters, updateParameters);
    final ControlledBigQueryDatasetResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreatedControlledGcpBigQueryDataset> createBigQueryDataset(
      UUID workspaceUuid, ApiCreateControlledGcpBigQueryDatasetRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    String resourceLocation = getResourceLocation(workspace, body.getDataset().getLocation());
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            resourceLocation,
            userRequest,
            WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    ApiGcpBigQueryDatasetCreationParameters dataset = body.getDataset();
    String datasetName =
        ControlledBigQueryDatasetHandler.getHandler()
            .generateCloudName(
                workspaceUuid,
                Optional.ofNullable(body.getDataset().getDatasetId())
                    .orElse(body.getCommon().getName()));

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    ControlledBigQueryDatasetResource resource =
        ControlledBigQueryDatasetResource.builder()
            .datasetName(datasetName)
            .projectId(projectId)
            .defaultPartitionLifetime(dataset.getDefaultPartitionLifetime())
            .defaultTableLifetime(dataset.getDefaultTableLifetime())
            .common(commonFields)
            .build();

    final ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, /*creationParameters=*/ null)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    UUID resourceId = createdDataset.getResourceId();
    var response =
        new ApiCreatedControlledGcpBigQueryDataset()
            .resourceId(resourceId)
            .bigQueryDataset(createdDataset.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDataset(UUID workspaceUuid, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceId, SamControlledResourceActions.DELETE_ACTION);
    logger.info(
        "Deleting controlled BQ dataset resource {} in workspace {}",
        resourceId.toString(),
        workspaceUuid.toString());
    controlledResourceService.deleteControlledResourceSync(workspaceUuid, resourceId, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpBigQueryDatasetResult> cloneBigQueryDataset(
      UUID workspaceUuid,
      UUID resourceId,
      @Valid ApiCloneControlledGcpBigQueryDatasetRequest body) {

    if (CloningInstructions.isReferenceClone(body.getCloningInstructions())
        && (!StringUtils.isEmpty(body.getDestinationDatasetName())
            || !StringUtils.isEmpty(body.getLocation()))) {
      throw new ValidationException(
          "Cannot set destination dataset name or location when cloning controlled dataset with COPY_REFERENCE or LINK_REFERENCE");
    }

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // This technically duplicates the first step of the flight as the clone flight is re-used for
    // cloneWorkspace, but this also saves us from launching and failing a flight if the user does
    // not have access to the resource.
    controlledResourceMetadataManager.validateCloneAction(
        userRequest, workspaceUuid, body.getDestinationWorkspaceId(), resourceId);

    final String jobId =
        controlledResourceService.cloneBigQueryDataset(
            workspaceUuid,
            resourceId,
            body.getDestinationWorkspaceId(),
            UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getDestinationDatasetName(),
            body.getLocation(),
            body.getCloningInstructions());
    final ApiCloneControlledGcpBigQueryDatasetResult result =
        fetchCloneBigQueryDatasetResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCloneControlledGcpBigQueryDatasetResult fetchCloneBigQueryDatasetResult(String jobId) {
    JobApiUtils.AsyncJobResult<ApiClonedControlledGcpBigQueryDataset> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ApiClonedControlledGcpBigQueryDataset.class);
    return new ApiCloneControlledGcpBigQueryDatasetResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .dataset(jobResult.getResult());
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpBigQueryDatasetResult> getCloneBigQueryDatasetResult(
      UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCloneControlledGcpBigQueryDatasetResult result = fetchCloneBigQueryDatasetResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpAiNotebookInstanceResult> createAiNotebookInstance(
      UUID workspaceUuid, @Valid ApiCreateControlledGcpAiNotebookInstanceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    String resourceLocation =
        getResourceLocation(workspace, body.getAiNotebookInstance().getLocation());
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            // AI notebook is a zonal resource. It's set here so that we can validate it against
            // policy. However, the notebook creation flight will compute a final location, which
            // could be a zone if a region is passed here. The assumption for policy is that a zone
            // is included inside of a region. The db entry is updated as part of the flight.
            resourceLocation,
            userRequest,
            WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);

    ControlledAiNotebookInstanceResource resource =
        ControlledAiNotebookInstanceResource.builder()
            .common(commonFields)
            .location(resourceLocation)
            .projectId(projectId)
            .instanceId(
                Optional.ofNullable(body.getAiNotebookInstance().getInstanceId())
                    .orElse(
                        ControlledAiNotebookHandler.getHandler()
                            .generateCloudName(workspaceUuid, commonFields.getName())))
            .build();

    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            body.getAiNotebookInstance(),
            commonFields.getIamRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest);

    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode((result.getJobReport())));
  }

  @Override
  public ResponseEntity<ApiAiNotebookCloudId> generateAiNotebookCloudInstanceId(
      UUID workspaceUuid, ApiGenerateGcpAiNotebookCloudIdRequestBody name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    String generatedAiNotebookName =
        ControlledAiNotebookHandler.getHandler()
            .generateCloudName(workspaceUuid, name.getAiNotebookName());
    var response =
        new ApiAiNotebookCloudId().generatedAiNotebookAiNotebookCloudId(generatedAiNotebookName);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpAiNotebookInstanceResource> updateAiNotebookInstance(
      UUID workspaceUuid,
      UUID resourceId,
      @Valid ApiUpdateControlledGcpAiNotebookInstanceRequestBody requestBody) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAiNotebookInstanceResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters()
            .setName(requestBody.getName())
            .setDescription(requestBody.getDescription());
    wsmResourceService.updateResource(
        userRequest, resource, commonUpdateParameters, requestBody.getUpdateParameters());
    ControlledAiNotebookInstanceResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpAiNotebookInstanceResult>
      getCreateAiNotebookInstanceResult(UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCreatedControlledGcpAiNotebookInstanceResult fetchNotebookInstanceCreateResult(
      String jobId) {
    JobApiUtils.AsyncJobResult<ControlledAiNotebookInstanceResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledAiNotebookInstanceResource.class);

    ApiGcpAiNotebookInstanceResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      ControlledAiNotebookInstanceResource resource = jobResult.getResult();
      apiResource = resource.toApiResource();
    }
    return new ApiCreatedControlledGcpAiNotebookInstanceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .aiNotebookInstance(apiResource);
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledGcpAiNotebookInstanceResult> deleteAiNotebookInstance(
      UUID workspaceUuid,
      UUID resourceId,
      @Valid ApiDeleteControlledGcpAiNotebookInstanceRequest body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceId, SamControlledResourceActions.DELETE_ACTION);
    ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteAiNotebookInstance workspace {} resource {}",
        workspaceUuid.toString(),
        resourceId.toString());
    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceUuid,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    ApiDeleteControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Traced
  @Override
  public ResponseEntity<ApiDeleteControlledGcpAiNotebookInstanceResult>
      getDeleteAiNotebookInstanceResult(UUID workspaceUuid, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiDeleteControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiDeleteControlledGcpAiNotebookInstanceResult fetchNotebookInstanceDeleteResult(
      String jobId) {
    JobApiUtils.AsyncJobResult<Void> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, Void.class);
    return new ApiDeleteControlledGcpAiNotebookInstanceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  @Traced
  @Override
  public ResponseEntity<ApiGcpAiNotebookInstanceResource> getAiNotebookInstance(
      UUID workspaceUuid, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAiNotebookInstanceResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.READ_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }
}
