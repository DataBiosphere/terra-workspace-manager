package bio.terra.workspace.app.controller;

import bio.terra.common.exception.BadRequestException;
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
import bio.terra.workspace.generated.model.ApiControlledDataprocClusterUpdateParameters;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpDataprocClusterRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGceInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpDataprocClusterResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGceInstanceResult;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiDataprocClusterCloudId;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpAiNotebookInstanceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpDataprocClusterRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpDataprocClusterResult;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGceInstanceRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGceInstanceResult;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiGceInstanceCloudId;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterResource;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.generated.model.ApiGcsBucketCloudName;
import bio.terra.workspace.generated.model.ApiGenerateGcpAiNotebookCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpBigQueryDatasetCloudIDRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpDataprocClusterCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpGceInstanceCloudIdRequestBody;
import bio.terra.workspace.generated.model.ApiGenerateGcpGcsBucketCloudNameRequestBody;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpDataprocClusterRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGceInstanceRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster.ControlledDataprocClusterHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster.ControlledDataprocClusterResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance.ControlledGceInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import com.google.common.base.Strings;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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

  private final WsmResourceService wsmResourceService;
  private final CrlService crlService;

  @Autowired
  public ControlledGcpResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      FeatureService featureService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      ControlledResourceService controlledResourceService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      CrlService crlService,
      WorkspaceService workspaceService,
      WsmResourceService wsmResourceService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        features,
        featureService,
        jobService,
        jobApiUtils,
        controlledResourceService,
        controlledResourceMetadataManager,
        workspaceService);
    this.wsmResourceService = wsmResourceService;
    this.crlService = crlService;
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledGcpGcsBucket> createBucket(
      UUID workspaceUuid, ApiCreateControlledGcpGcsBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.GCP);
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
                        .generateCloudName(workspaceUuid, commonFields.getName())
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
                GcpResourceConstants.DEFAULT_REGION)
        : requestedLocation;
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> deleteBucket(
      UUID workspaceUuid, UUID resourceUuid, ApiDeleteControlledGcpGcsBucketRequest body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteBucket workspace {} resource {}", workspaceUuid.toString(), resourceUuid.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl.getId(),
            workspaceUuid,
            resourceUuid,
            /* forceDelete= */ false,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    return getDeleteResult(jobId);
  }

  @WithSpan
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

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucket(UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGcsBucketResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
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

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> updateGcsBucket(
      UUID workspaceUuid, UUID resourceUuid, ApiUpdateControlledGcpGcsBucketRequestBody body) {
    logger.info("Updating bucket resourceId {} workspaceUuid {}", resourceUuid, workspaceUuid);
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGcsBucketResource bucketResource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);
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
            .getControlledResource(workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> cloneGcsBucket(
      UUID workspaceUuid, UUID resourceUuid, ApiCloneControlledGcpGcsBucketRequest body) {
    logger.info("Cloning GCS bucket resourceId {} workspaceUuid {}", resourceUuid, workspaceUuid);
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGcsBucketResource resource =
        controlledResourceMetadataManager
            .validateCloneAction(
                userRequest, workspaceUuid, body.getDestinationWorkspaceId(), resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    CloningInstructions cloningInstructions =
        resource.computeCloningInstructions(body.getCloningInstructions());
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.CONTROLLED, cloningInstructions);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);
    workspaceService.validateCloneWorkspaceAndContextState(
        body.getDestinationWorkspaceId(), CloudPlatform.GCP, cloningInstructions);

    if (cloningInstructions.isReferenceClone()
        && (!StringUtils.isEmpty(body.getBucketName())
            || !StringUtils.isEmpty(body.getLocation()))) {
      throw new ValidationException(
          "Cannot set bucket or location when cloning a controlled bucket with COPY_REFERENCE or LINK_REFERENCE");
    }

    String jobId =
        controlledResourceService.cloneGcsBucket(
            resource,
            body.getDestinationWorkspaceId(),
            UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getBucketName(),
            body.getLocation(),
            cloningInstructions);
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

  @WithSpan
  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> getCloneGcsBucketResult(
      UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCloneControlledGcpGcsBucketResult result = fetchCloneGcsBucketResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDataset(
      UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledBigQueryDatasetResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
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

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> updateBigQueryDataset(
      UUID workspaceUuid,
      UUID resourceUuid,
      ApiUpdateControlledGcpBigQueryDatasetRequestBody body) {
    logger.info("Updating dataset resourceId {} workspaceUuid {}", resourceUuid, workspaceUuid);
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledBigQueryDatasetResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);

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
            .getControlledResource(workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledGcpBigQueryDataset> createBigQueryDataset(
      UUID workspaceUuid, ApiCreateControlledGcpBigQueryDatasetRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    CloudContext cloudContext =
        workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.GCP);
    GcpCloudContext gcpCloudContext = cloudContext.castByEnum(CloudPlatform.GCP);
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

    ControlledBigQueryDatasetResource resource =
        ControlledBigQueryDatasetResource.builder()
            .datasetName(datasetName)
            .projectId(gcpCloudContext.getGcpProjectId())
            .defaultPartitionLifetime(dataset.getDefaultPartitionLifetime())
            .defaultTableLifetime(dataset.getDefaultTableLifetime())
            .common(commonFields)
            .build();

    final ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, /* creationParameters= */ null)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    UUID resourceUuid = createdDataset.getResourceId();
    var response =
        new ApiCreatedControlledGcpBigQueryDataset()
            .resourceId(resourceUuid)
            .bigQueryDataset(createdDataset.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDataset(UUID workspaceUuid, UUID resourceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);

    logger.info(
        "Deleting controlled BQ dataset resource {} in workspace {}",
        resourceUuid.toString(),
        workspaceUuid.toString());
    controlledResourceService.deleteControlledResourceSync(
        workspaceUuid, resourceUuid, /* forceDelete= */ false, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpBigQueryDatasetResult> cloneBigQueryDataset(
      UUID workspaceUuid, UUID resourceUuid, ApiCloneControlledGcpBigQueryDatasetRequest body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledBigQueryDatasetResource resource =
        controlledResourceMetadataManager
            .validateCloneAction(
                userRequest, workspaceUuid, body.getDestinationWorkspaceId(), resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    CloningInstructions cloningInstructions =
        resource.computeCloningInstructions(body.getCloningInstructions());
    ResourceValidationUtils.validateCloningInstructions(
        StewardshipType.CONTROLLED, cloningInstructions);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);
    workspaceService.validateCloneWorkspaceAndContextState(
        body.getDestinationWorkspaceId(), CloudPlatform.GCP, cloningInstructions);

    if (CloningInstructions.isReferenceClone(body.getCloningInstructions())
        && (!StringUtils.isEmpty(body.getDestinationDatasetName())
            || !StringUtils.isEmpty(body.getLocation()))) {
      throw new ValidationException(
          "Cannot set destination dataset name or location when cloning controlled dataset with COPY_REFERENCE or LINK_REFERENCE");
    }

    String jobId =
        controlledResourceService.cloneBigQueryDataset(
            resource,
            body.getDestinationWorkspaceId(),
            UUID.randomUUID(), // resourceId is not pre-allocated for individual clone endpoints
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getDestinationDatasetName(),
            body.getLocation(),
            cloningInstructions);
    ApiCloneControlledGcpBigQueryDatasetResult result = fetchCloneBigQueryDatasetResult(jobId);
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
      UUID workspaceUuid, ApiCreateControlledGcpAiNotebookInstanceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    CloudContext cloudContext =
        workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.GCP);
    GcpCloudContext gcpCloudContext = cloudContext.castByEnum(CloudPlatform.GCP);
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

    ControlledAiNotebookInstanceResource resource =
        ControlledAiNotebookInstanceResource.builder()
            .common(commonFields)
            .location(resourceLocation)
            .projectId(gcpCloudContext.getGcpProjectId())
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
      UUID resourceUuid,
      ApiUpdateControlledGcpAiNotebookInstanceRequestBody requestBody) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAiNotebookInstanceResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);

    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters()
            .setName(requestBody.getName())
            .setDescription(requestBody.getDescription());
    wsmResourceService.updateResource(
        userRequest, resource, commonUpdateParameters, requestBody.getUpdateParameters());
    ControlledAiNotebookInstanceResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceUuid)
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

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledGcpAiNotebookInstanceResult> deleteAiNotebookInstance(
      UUID workspaceUuid, UUID resourceUuid, ApiDeleteControlledGcpAiNotebookInstanceRequest body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);

    ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteAiNotebookInstance workspace {} resource {}",
        workspaceUuid.toString(),
        resourceUuid.toString());
    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl.getId(),
            workspaceUuid,
            resourceUuid,
            /* forceDelete= */ false,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    ApiDeleteControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledGcpAiNotebookInstanceResult>
      getDeleteAiNotebookInstanceResult(UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
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

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpAiNotebookInstanceResource> getAiNotebookInstance(
      UUID workspaceUuid, UUID resourceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAiNotebookInstanceResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledGcpGceInstanceResult> createGceInstance(
      UUID workspaceUuid, ApiCreateControlledGcpGceInstanceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    String resourceLocation = getResourceLocation(workspace, body.getGceInstance().getZone());
    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            // A GCE instance is a zonal resource. It's set here so that we can validate it against
            // policy. However, the creation flight will compute a final location, which
            // could be a zone if a region is passed here. The assumption for policy is that a zone
            // is included inside of a region. The db entry is updated as part of the flight.
            resourceLocation,
            userRequest,
            WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    CloudContext cloudContext =
        workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.GCP);
    GcpCloudContext gcpCloudContext = cloudContext.castByEnum(CloudPlatform.GCP);
    String projectId = gcpCloudContext.getGcpProjectId();

    ControlledGceInstanceResource resource =
        ControlledGceInstanceResource.builder()
            .common(commonFields)
            .zone(resourceLocation)
            .projectId(projectId)
            .instanceId(
                Optional.ofNullable(body.getGceInstance().getInstanceId())
                    .orElse(
                        ControlledGceInstanceHandler.getHandler()
                            .generateCloudName(workspaceUuid, commonFields.getName())))
            .build();

    String jobId =
        controlledResourceService.createGceInstance(
            resource,
            body.getGceInstance(),
            commonFields.getIamRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest);

    ApiCreatedControlledGcpGceInstanceResult result = fetchGceInstanceCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode((result.getJobReport())));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiGceInstanceCloudId> generateGceInstanceCloudId(
      UUID workspaceUuid, ApiGenerateGcpGceInstanceCloudIdRequestBody name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    String generatedGceInstanceName =
        ControlledGceInstanceHandler.getHandler()
            .generateCloudName(workspaceUuid, name.getInstanceName());
    var response =
        new ApiGceInstanceCloudId().generatedGceInstanceCloudId(generatedGceInstanceName);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpGceInstanceResource> updateGceInstance(
      UUID workspaceUuid,
      UUID resourceUuid,
      ApiUpdateControlledGcpGceInstanceRequestBody requestBody) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGceInstanceResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);
    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters()
            .setName(requestBody.getName())
            .setDescription(requestBody.getDescription());
    wsmResourceService.updateResource(
        userRequest, resource, commonUpdateParameters, requestBody.getUpdateParameters());
    ControlledGceInstanceResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledGcpGceInstanceResult> getCreateGceInstanceResult(
      UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreatedControlledGcpGceInstanceResult result = fetchGceInstanceCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCreatedControlledGcpGceInstanceResult fetchGceInstanceCreateResult(String jobId) {
    JobApiUtils.AsyncJobResult<ControlledGceInstanceResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledGceInstanceResource.class);

    ApiGcpGceInstanceResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      ControlledGceInstanceResource resource = jobResult.getResult();
      apiResource = resource.toApiResource();
    }
    return new ApiCreatedControlledGcpGceInstanceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .gceInstance(apiResource);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledGcpGceInstanceResult> deleteGceInstance(
      UUID workspaceUuid, UUID resourceUuid, ApiDeleteControlledGcpGceInstanceRequest body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);
    ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteGceInstance workspace {} resource {}",
        workspaceUuid.toString(),
        resourceUuid.toString());
    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl.getId(),
            workspaceUuid,
            resourceUuid,
            /* forceDelete= */ false,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    ApiDeleteControlledGcpGceInstanceResult result = fetchGceInstanceDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledGcpGceInstanceResult> getDeleteGceInstanceResult(
      UUID workspaceUuid, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiDeleteControlledGcpGceInstanceResult result = fetchGceInstanceDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiDeleteControlledGcpGceInstanceResult fetchGceInstanceDeleteResult(String jobId) {
    JobApiUtils.AsyncJobResult<Void> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, Void.class);
    return new ApiDeleteControlledGcpGceInstanceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpGceInstanceResource> getGceInstance(
      UUID workspaceUuid, UUID resourceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGceInstanceResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledGcpDataprocClusterResult> createDataprocCluster(
      UUID workspaceUuid, ApiCreateControlledGcpDataprocClusterRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.getSamAction(body.getCommon()));
    CloudContext cloudContext =
        workspaceService.validateWorkspaceAndContextState(workspace, CloudPlatform.GCP);
    GcpCloudContext gcpCloudContext = cloudContext.castByEnum(CloudPlatform.GCP);

    String resourceRegion = getResourceLocation(workspace, body.getDataprocCluster().getRegion());

    // Validate existence and user write access to provided staging and temp buckets
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        workspaceUuid,
        body.getDataprocCluster().getConfigBucket(),
        SamControlledResourceActions.WRITE_ACTION);
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest,
        workspaceUuid,
        body.getDataprocCluster().getTempBucket(),
        SamControlledResourceActions.WRITE_ACTION);

    ControlledResourceFields commonFields =
        toCommonFields(
            workspaceUuid,
            body.getCommon(),
            resourceRegion,
            userRequest,
            WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER);

    ControlledDataprocClusterResource resource =
        ControlledDataprocClusterResource.builder()
            .common(commonFields)
            .region(resourceRegion)
            .projectId(gcpCloudContext.getGcpProjectId())
            .clusterId(
                Optional.ofNullable(body.getDataprocCluster().getClusterId())
                    .orElse(
                        ControlledDataprocClusterHandler.getHandler()
                            .generateCloudName(workspaceUuid, commonFields.getName())))
            .build();

    logger.info(
        "createDataprocCluster workspace {} resource {}", workspaceUuid, resource.getResourceId());
    String jobId =
        controlledResourceService.createDataprocCluster(
            resource,
            body.getDataprocCluster(),
            commonFields.getIamRole(),
            body.getJobControl(),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"),
            userRequest);

    ApiCreatedControlledGcpDataprocClusterResult result = fetchDataprocClusterCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode((result.getJobReport())));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiCreatedControlledGcpDataprocClusterResult>
      getCreateDataprocClusterResult(UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCreatedControlledGcpDataprocClusterResult result = fetchDataprocClusterCreateResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDataprocClusterCloudId> generateDataprocClusterCloudId(
      UUID workspaceUuid, ApiGenerateGcpDataprocClusterCloudIdRequestBody name) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    String generatedDataprocClusterCloudId =
        ControlledDataprocClusterHandler.getHandler()
            .generateCloudName(workspaceUuid, name.getDataprocClusterName());
    var response =
        new ApiDataprocClusterCloudId()
            .generatedDataprocClusterCloudId(generatedDataprocClusterCloudId);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpDataprocClusterResource> updateDataprocCluster(
      UUID workspaceUuid,
      UUID resourceUuid,
      ApiUpdateControlledGcpDataprocClusterRequestBody requestBody) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledDataprocClusterResource resource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);

    CommonUpdateParameters commonUpdateParameters =
        new CommonUpdateParameters()
            .setName(requestBody.getName())
            .setDescription(requestBody.getDescription());

    // Validate update parameter values to ensure there are no invalid configurations that can cause
    // dismal failures.
    ApiControlledDataprocClusterUpdateParameters updateParameters =
        requestBody.getUpdateParameters();
    if (updateParameters != null) {
      if (updateParameters.getAutoscalingPolicy() != null
          && (updateParameters.getNumPrimaryWorkers() != null
              || updateParameters.getNumSecondaryWorkers() != null
              || updateParameters.getLifecycleConfig() != null
              || updateParameters.getGracefulDecommissionTimeout() != null)) {
        throw new BadRequestException(
            "Cluster autoscaling policy cannot be updated in tandem with other attribute updates.");
      }
      if (updateParameters.getLifecycleConfig() != null) {
        // Cluster scheduled deletion configurations cannot be updated in tandem with other
        // attributes.
        if (updateParameters.getNumPrimaryWorkers() != null
            || updateParameters.getNumSecondaryWorkers() != null
            || updateParameters.getAutoscalingPolicy() != null) {
          throw new BadRequestException(
              "Cluster scheduled deletion configurations cannot be updated in tandem with other attribute updates.");
        }
        // Can only specify one of autoDeleteTtl or autoDeleteTime
        if (updateParameters.getLifecycleConfig().getAutoDeleteTtl() != null
            && updateParameters.getLifecycleConfig().getAutoDeleteTime() != null) {
          throw new BadRequestException("Cannot specify both autoDeleteTtl and autoDeleteTime");
        }
        // Can only specify 0 or more than 1 primary worker
        if (Objects.equals(updateParameters.getNumPrimaryWorkers(), 1)) {
          throw new BadRequestException("Provide more than 1 primary worker, or none.");
        }
      }
    }

    logger.info(
        "updateDataprocCluster workspace {} resource {}",
        workspaceUuid.toString(),
        resourceUuid.toString());

    wsmResourceService.updateResource(
        userRequest, resource, commonUpdateParameters, updateParameters);
    ControlledDataprocClusterResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  private ApiCreatedControlledGcpDataprocClusterResult fetchDataprocClusterCreateResult(
      String jobId) {
    JobApiUtils.AsyncJobResult<ControlledDataprocClusterResource> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ControlledDataprocClusterResource.class);

    ApiGcpDataprocClusterResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      ControlledDataprocClusterResource resource = jobResult.getResult();
      apiResource = resource.toApiResource();
    }
    return new ApiCreatedControlledGcpDataprocClusterResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .dataprocCluster(apiResource);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledGcpDataprocClusterResult> deleteDataprocCluster(
      UUID workspaceUuid, UUID resourceUuid, ApiDeleteControlledGcpDataprocClusterRequest body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    controlledResourceMetadataManager.validateControlledResourceAndAction(
        userRequest, workspaceUuid, resourceUuid, SamControlledResourceActions.DELETE_ACTION);
    workspaceService.validateWorkspaceAndContextState(workspaceUuid, CloudPlatform.GCP);

    ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteDataprocCluster workspace {} resource {}",
        workspaceUuid.toString(),
        resourceUuid.toString());
    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl.getId(),
            workspaceUuid,
            resourceUuid,
            /* forceDelete= */ false,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    ApiDeleteControlledGcpDataprocClusterResult result = fetchDataprocClusterDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiDeleteControlledGcpDataprocClusterResult> getDeleteDataprocClusterResult(
      UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiDeleteControlledGcpDataprocClusterResult result = fetchDataprocClusterDeleteResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiDeleteControlledGcpDataprocClusterResult fetchDataprocClusterDeleteResult(
      String jobId) {
    JobApiUtils.AsyncJobResult<Void> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, Void.class);
    return new ApiDeleteControlledGcpDataprocClusterResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiGcpDataprocClusterResource> getDataprocCluster(
      UUID workspaceUuid, UUID resourceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledDataprocClusterResource resource =
        controlledResourceMetadataManager
            .validateWorkspaceOrControlledResourceReadAccess(
                userRequest, workspaceUuid, resourceUuid)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }
}
