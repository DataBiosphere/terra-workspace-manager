package bio.terra.workspace.app.controller;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.generated.controller.ControlledGcpResourceApi;
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
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.exception.InvalidControlledResourceException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.Optional;
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
public class ControlledGcpResourceApiController extends ControlledResourceControllerBase
    implements ControlledGcpResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledGcpResourceApiController.class);

  private final ControlledResourceService controlledResourceService;
  private final WorkspaceService workspaceService;
  private final JobService jobService;
  private final PetSaService petSaService;

  @Autowired
  public ControlledGcpResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      ControlledResourceService controlledResourceService,
      SamService samService,
      WorkspaceService workspaceService,
      JobService jobService,
      PetSaService petSaService) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.controlledResourceService = controlledResourceService;
    this.workspaceService = workspaceService;
    this.jobService = jobService;
    this.petSaService = petSaService;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpGcsBucket> createBucket(
      UUID workspaceId, @Valid ApiCreateControlledGcpGcsBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(workspaceId, body.getCommon(), userRequest);

    ControlledGcsBucketResource resource =
        ControlledGcsBucketResource.builder()
            .bucketName(body.getGcsBucket().getName())
            .common(commonFields)
            .build();

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

  @Override
  public ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> deleteBucket(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledGcpGcsBucketRequest body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteBucket workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    return getDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> getDeleteBucketResult(
      UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getDeleteResult(jobId, userRequest);
  }

  private ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> getDeleteResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    final AsyncJobResult<Void> jobResult =
        jobService.retrieveAsyncJobResult(jobId, Void.class, userRequest);
    var response =
        new ApiDeleteControlledGcpGcsBucketResult()
            .jobReport(jobResult.getJobReport())
            .errorReport(jobResult.getApiErrorReport());
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucket(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledGcsBucketResource resource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> updateGcsBucket(
      UUID workspaceId, UUID resourceId, @Valid ApiUpdateControlledGcpGcsBucketRequestBody body) {
    logger.info("Updating bucket resourceId {} workspaceId {}", resourceId, workspaceId);
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResource resource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    if (resource.getResourceType() != WsmResourceType.CONTROLLED_GCP_GCS_BUCKET) {
      throw new InvalidControlledResourceException(
          String.format("Resource %s is not a GCS Bucket", resourceId));
    }
    final ControlledGcsBucketResource bucketResource =
        resource.castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    controlledResourceService.updateGcsBucket(
        bucketResource,
        body.getUpdateParameters(),
        userRequest,
        body.getName(),
        body.getDescription());

    // Retrieve and cast response to ApiGcpGcsBucketResource
    final ControlledGcsBucketResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> cloneGcsBucket(
      UUID workspaceId, UUID resourceId, @Valid ApiCloneControlledGcpGcsBucketRequest body) {
    logger.info("Cloning GCS bucket resourceId {} workspaceId {}", resourceId, workspaceId);

    final AuthenticatedUserRequest petRequest = getPetRequest(workspaceId);

    final String jobId =
        controlledResourceService.cloneGcsBucket(
            workspaceId,
            resourceId,
            body.getDestinationWorkspaceId(),
            body.getJobControl(),
            petRequest,
            body.getName(),
            body.getDescription(),
            body.getBucketName(),
            body.getLocation(),
            body.getCloningInstructions());
    final ApiCloneControlledGcpGcsBucketResult result =
        fetchCloneGcsBucketResult(jobId, petRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCloneControlledGcpGcsBucketResult fetchCloneGcsBucketResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    final AsyncJobResult<ApiClonedControlledGcpGcsBucket> jobResult =
        jobService.retrieveAsyncJobResult(
            jobId, ApiClonedControlledGcpGcsBucket.class, userRequest);
    return new ApiCloneControlledGcpGcsBucketResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .bucket(jobResult.getResult());
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> getCloneGcsBucketResult(
      UUID workspaceId, String jobId) {
    // TODO: validate correct workspace ID. PF-859
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCloneControlledGcpGcsBucketResult result = fetchCloneGcsBucketResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDataset(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    ControlledBigQueryDatasetResource resource =
        controlledResource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> updateBigQueryDataset(
      UUID workspaceId, UUID resourceId, ApiUpdateControlledGcpBigQueryDatasetRequestBody body) {
    logger.info("Updating dataset resourceId {} workspaceId {}", resourceId, workspaceId);
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledBigQueryDatasetResource resource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    controlledResourceService.updateBqDataset(
        resource, body.getUpdateParameters(), userRequest, body.getName(), body.getDescription());

    final ControlledBigQueryDatasetResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpBigQueryDataset> createBigQueryDataset(
      UUID workspaceId, ApiCreateControlledGcpBigQueryDatasetRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(workspaceId, body.getCommon(), userRequest);

    // We need to retrieve the project id so it can be used in the BQ dataset attributes.
    String projectId = workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest);

    ControlledBigQueryDatasetResource resource =
        ControlledBigQueryDatasetResource.builder()
            .datasetName(
                Optional.ofNullable(body.getDataset().getDatasetId())
                    .orElse(body.getCommon().getName()))
            .projectId(projectId)
            .common(commonFields)
            .build();

    final ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createControlledResourceSync(
                resource, commonFields.getIamRole(), userRequest, body.getDataset())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    var response =
        new ApiCreatedControlledGcpBigQueryDataset()
            .resourceId(createdDataset.getResourceId())
            .bigQueryDataset(createdDataset.toApiResource());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteBigQueryDataset(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info(
        "Deleting controlled BQ dataset resource {} in workspace {}",
        resourceId.toString(),
        workspaceId.toString());
    controlledResourceService.deleteControlledResourceSync(workspaceId, resourceId, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpAiNotebookInstanceResult> createAiNotebookInstance(
      UUID workspaceId, @Valid ApiCreateControlledGcpAiNotebookInstanceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(workspaceId, body.getCommon(), userRequest);
    String projectId = workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest);

    ControlledAiNotebookInstanceResource resource =
        ControlledAiNotebookInstanceResource.builder()
            .common(commonFields)
            .location(body.getAiNotebookInstance().getLocation())
            .projectId(projectId)
            .instanceId(
                Optional.ofNullable(body.getAiNotebookInstance().getInstanceId())
                    .orElse(
                        ControlledAiNotebookInstanceResource.generateInstanceId(
                            commonFields.getAssignedUser())))
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
        fetchNotebookInstanceCreateResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode((result.getJobReport())));
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpAiNotebookInstanceResult>
      getCreateAiNotebookInstanceResult(UUID workspaceId, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceCreateResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCreatedControlledGcpAiNotebookInstanceResult fetchNotebookInstanceCreateResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    AsyncJobResult<ControlledAiNotebookInstanceResource> jobResult =
        jobService.retrieveAsyncJobResult(
            jobId, ControlledAiNotebookInstanceResource.class, userRequest);

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

  @Override
  public ResponseEntity<ApiDeleteControlledGcpAiNotebookInstanceResult> deleteAiNotebookInstance(
      UUID workspaceId,
      UUID resourceId,
      @Valid ApiDeleteControlledGcpAiNotebookInstanceRequest body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteAiNotebookInstance workspace {} resource {}",
        workspaceId.toString(),
        resourceId.toString());
    String jobId =
        controlledResourceService.deleteControlledResourceAsync(
            jobControl,
            workspaceId,
            resourceId,
            getAsyncResultEndpoint(jobControl.getId(), "delete-result"),
            userRequest);
    ApiDeleteControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceDeleteResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiDeleteControlledGcpAiNotebookInstanceResult>
      getDeleteAiNotebookInstanceResult(UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiDeleteControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceDeleteResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private ApiDeleteControlledGcpAiNotebookInstanceResult fetchNotebookInstanceDeleteResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    AsyncJobResult<Void> jobResult =
        jobService.retrieveAsyncJobResult(jobId, Void.class, userRequest);
    return new ApiDeleteControlledGcpAiNotebookInstanceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  @Override
  public ResponseEntity<ApiGcpAiNotebookInstanceResource> getAiNotebookInstance(
      UUID workspaceId, UUID resourceId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledAiNotebookInstanceResource resource =
        controlledResourceService
            .getControlledResource(workspaceId, resourceId, userRequest)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // TODO: security check for: workspaceService.getAuthorizedRequiredGcpProject(workspaceId,
    //  userRequest));
    return new ResponseEntity<>(resource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpBigQueryDatasetResult> cloneBigQueryDataset(
      UUID workspaceId, UUID resourceId, @Valid ApiCloneControlledGcpBigQueryDatasetRequest body) {
    final AuthenticatedUserRequest petRequest = getPetRequest(workspaceId);

    final String jobId =
        controlledResourceService.cloneBigQueryDataset(
            workspaceId,
            resourceId,
            body.getDestinationWorkspaceId(),
            body.getJobControl(),
            petRequest,
            body.getName(),
            body.getDescription(),
            body.getDestinationDatasetName(),
            body.getLocation(),
            body.getCloningInstructions());
    final ApiCloneControlledGcpBigQueryDatasetResult result =
        fetchCloneBigQueryDatasetResult(jobId, petRequest);
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  private ApiCloneControlledGcpBigQueryDatasetResult fetchCloneBigQueryDatasetResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    final AsyncJobResult<ApiClonedControlledGcpBigQueryDataset> jobResult =
        jobService.retrieveAsyncJobResult(
            jobId, ApiClonedControlledGcpBigQueryDataset.class, userRequest);
    return new ApiCloneControlledGcpBigQueryDatasetResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .dataset(jobResult.getResult());
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpBigQueryDatasetResult> getCloneBigQueryDatasetResult(
      UUID workspaceId, String jobId) {
    // TODO: validate correct workspace ID. PF-859
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCloneControlledGcpBigQueryDatasetResult result =
        fetchCloneBigQueryDatasetResult(jobId, userRequest);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  private AuthenticatedUserRequest getPetRequest(UUID workspaceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return petSaService
        .getWorkspacePetCredentials(workspaceId, userRequest)
        .orElseThrow(
            () ->
                new BadRequestException(
                    String.format(
                        "Pet SA credentials not found for user %s on workspace %s",
                        userRequest.getEmail(), workspaceId)));
  }
}
