package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledGcpResourceApi;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
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
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants;
import com.google.common.base.Strings;
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

  private final ControlledResourceService controlledResourceService;
  private final WorkspaceService workspaceService;
  private final JobService jobService;
  private final GcpCloudContextService gcpCloudContextService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;

  @Autowired
  public ControlledGcpResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      ControlledResourceService controlledResourceService,
      SamService samService,
      WorkspaceService workspaceService,
      JobService jobService,
      GcpCloudContextService gcpCloudContextService,
      ControlledResourceMetadataManager controlledResourceMetadataManager) {
    super(authenticatedUserRequestFactory, request, controlledResourceService, samService);
    this.controlledResourceService = controlledResourceService;
    this.workspaceService = workspaceService;
    this.jobService = jobService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpGcsBucket> createBucket(
      UUID workspaceUuid, @Valid ApiCreateControlledGcpGcsBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);
    ControlledGcsBucketResource resource =
        ControlledGcsBucketResource.builder()
            .bucketName(
                StringUtils.isEmpty(body.getGcsBucket().getName())
                    ? ControlledGcsBucketHandler.getHandler()
                        .generateCloudName(commonFields.getWorkspaceId(), commonFields.getName())
                    : body.getGcsBucket().getName())
            .common(commonFields)
            .build();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, resource.getCategory().getSamCreateResourceAction());

    body.getGcsBucket().location(getResourceLocation(workspace, body.getGcsBucket().getLocation()));

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

  @Override
  public ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> getDeleteBucketResult(
      UUID workspaceUuid, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    return getDeleteResult(jobId);
  }

  private ResponseEntity<ApiDeleteControlledGcpGcsBucketResult> getDeleteResult(String jobId) {
    final AsyncJobResult<Void> jobResult = jobService.retrieveAsyncJobResult(jobId, Void.class);
    var response =
        new ApiDeleteControlledGcpGcsBucketResult()
            .jobReport(jobResult.getJobReport())
            .errorReport(jobResult.getApiErrorReport());
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

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

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> updateGcsBucket(
      UUID workspaceUuid, UUID resourceId, @Valid ApiUpdateControlledGcpGcsBucketRequestBody body) {
    logger.info("Updating bucket resourceId {} workspaceUuid {}", resourceId, workspaceUuid);
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledGcsBucketResource bucketResource =
        controlledResourceMetadataManager
            .validateControlledResourceAndAction(
                userRequest, workspaceUuid, resourceId, SamControlledResourceActions.EDIT_ACTION)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    controlledResourceService.updateGcsBucket(
        bucketResource,
        body.getUpdateParameters(),
        body.getName(),
        body.getDescription(),
        userRequest);

    // Retrieve and cast response to ApiGcpGcsBucketResource
    final ControlledGcsBucketResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> cloneGcsBucket(
      UUID workspaceUuid, UUID resourceId, @Valid ApiCloneControlledGcpGcsBucketRequest body) {
    logger.info("Cloning GCS bucket resourceId {} workspaceUuid {}", resourceId, workspaceUuid);

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
    final AsyncJobResult<ApiClonedControlledGcpGcsBucket> jobResult =
        jobService.retrieveAsyncJobResult(jobId, ApiClonedControlledGcpGcsBucket.class);
    return new ApiCloneControlledGcpGcsBucketResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .bucket(jobResult.getResult());
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> getCloneGcsBucketResult(
      UUID workspaceUuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    ApiCloneControlledGcpGcsBucketResult result = fetchCloneGcsBucketResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

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
    controlledResourceService.updateBqDataset(
        resource, body.getUpdateParameters(), body.getName(), body.getDescription());

    final ControlledBigQueryDatasetResource updatedResource =
        controlledResourceService
            .getControlledResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);
    return new ResponseEntity<>(updatedResource.toApiResource(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpBigQueryDataset> createBigQueryDataset(
      UUID workspaceUuid, ApiCreateControlledGcpBigQueryDatasetRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);
    // Check authz before reading the projectId from workspace DB.
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    ControlledBigQueryDatasetResource resource =
        ControlledBigQueryDatasetResource.builder()
            .datasetName(
                ControlledBigQueryDatasetHandler.getHandler()
                    .generateCloudName(
                        null,
                        Optional.ofNullable(body.getDataset().getDatasetId())
                            .orElse(body.getCommon().getName())))
            .projectId(projectId)
            .common(commonFields)
            .build();

    body.getDataset().location(getResourceLocation(workspace, body.getDataset().getLocation()));

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
  public ResponseEntity<ApiCreatedControlledGcpAiNotebookInstanceResult> createAiNotebookInstance(
      UUID workspaceUuid, @Valid ApiCreateControlledGcpAiNotebookInstanceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResourceFields commonFields =
        toCommonFields(workspaceUuid, body.getCommon(), userRequest);
    // Check authz before reading the projectId from workspace DB.
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, ControllerValidationUtils.samCreateAction(commonFields));
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);

    ControlledAiNotebookInstanceResource resource =
        ControlledAiNotebookInstanceResource.builder()
            .common(commonFields)
            .location(body.getAiNotebookInstance().getLocation())
            .projectId(projectId)
            .instanceId(
                Optional.ofNullable(body.getAiNotebookInstance().getInstanceId())
                    .orElse(
                        ControlledAiNotebookHandler.getHandler()
                            .generateCloudName(null, commonFields.getName())))
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

    controlledResourceService.updateAiNotebookInstance(
        resource,
        requestBody.getUpdateParameters(),
        requestBody.getName(),
        requestBody.getDescription(),
        userRequest);

    final ControlledAiNotebookInstanceResource updatedResource =
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
    AsyncJobResult<ControlledAiNotebookInstanceResource> jobResult =
        jobService.retrieveAsyncJobResult(jobId, ControlledAiNotebookInstanceResource.class);

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
    AsyncJobResult<Void> jobResult = jobService.retrieveAsyncJobResult(jobId, Void.class);
    return new ApiDeleteControlledGcpAiNotebookInstanceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

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

  @Override
  public ResponseEntity<ApiCloneControlledGcpBigQueryDatasetResult> cloneBigQueryDataset(
      UUID workspaceUuid,
      UUID resourceId,
      @Valid ApiCloneControlledGcpBigQueryDatasetRequest body) {
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
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  private ApiCloneControlledGcpBigQueryDatasetResult fetchCloneBigQueryDatasetResult(String jobId) {
    final AsyncJobResult<ApiClonedControlledGcpBigQueryDataset> jobResult =
        jobService.retrieveAsyncJobResult(jobId, ApiClonedControlledGcpBigQueryDataset.class);
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
}
