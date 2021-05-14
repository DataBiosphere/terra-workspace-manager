package bio.terra.workspace.app.controller;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.utils.ControllerUtils;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.generated.controller.ControlledGcpResourceApi;
import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
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
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledResourceRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
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
public class ControlledGcpResourceApiController implements ControlledGcpResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledGcpResourceApiController.class);

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final ControlledResourceService controlledResourceService;
  private final WorkspaceService workspaceService;
  private final ControlledResourceMetadataManager controlledResourceMetadataManager;
  private final HttpServletRequest request;
  private final JobService jobService;

  @Autowired
  public ControlledGcpResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledResourceService controlledResourceService,
      WorkspaceService workspaceService,
      JobService jobService,
      ControlledResourceMetadataManager controlledResourceMetadataManager,
      @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
          HttpServletRequest request) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.controlledResourceService = controlledResourceService;
    this.workspaceService = workspaceService;
    this.controlledResourceMetadataManager = controlledResourceMetadataManager;
    this.request = request;
    this.jobService = jobService;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpGcsBucket> createBucket(
      UUID workspaceId, @Valid ApiCreateControlledGcpGcsBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledGcsBucketResource resource =
        ControlledGcsBucketResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(
                Optional.ofNullable(body.getCommon().getPrivateResourceUser())
                    .map(ApiPrivateResourceUser::getUserName)
                    .orElse(null))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .bucketName(body.getGcsBucket().getName())
            .build();

    List<ControlledResourceIamRole> privateRoles = privateRolesFromBody(body.getCommon());

    final ControlledGcsBucketResource createdBucket =
        controlledResourceService.createBucket(
            resource, body.getGcsBucket(), privateRoles, userRequest);
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
            ControllerUtils.getAsyncResultEndpoint(request, jobControl.getId(), "delete-result"),
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
    return new ResponseEntity<>(
        response, HttpStatus.valueOf(response.getJobReport().getStatusCode()));
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> getBucket(UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    try {
      ApiGcpGcsBucketResource response =
          controlledResource.castToGcsBucketResource().toApiResource();
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (InvalidMetadataException ex) {
      throw new BadRequestException(
          String.format(
              "Resource %s in workspace %s is not a controlled GCS bucket.",
              resourceId, workspaceId));
    }
  }

  @Override
  public ResponseEntity<ApiGcpGcsBucketResource> updateGcsBucket(
      UUID workspaceId, UUID resourceId, @Valid ApiUpdateControlledGcpGcsBucketRequestBody body) {
    logger.info("Updating bucket resourceId {} workspaceId {}", resourceId, workspaceId);
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    final ControlledResource resource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    if (resource.getResourceType() != WsmResourceType.GCS_BUCKET) {
      // TODO: is there a better exception type here?
      throw new NotFoundException(String.format("Resource %s is not a GCS Bucket", resourceId));
    }
    final ControlledGcsBucketResource bucketResource = resource.castToGcsBucketResource();
    final ControlledGcsBucketResource updatedResource =
        controlledResourceService.updateGcsBucket(
            bucketResource, body.getUpdateParameters(), userRequest, jobControl);

    // Retrieve and cast response to UpdateControlledGcpGcsBucketResponse

    return controlledResourceToResponseEntity(
        workspaceId, resourceId, userRequest, r -> r.castToGcsBucketResource().toApiResource());
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDataset(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String projectId = workspaceService.getRequiredGcpProject(workspaceId);
    return controlledResourceToResponseEntity(
        workspaceId,
        resourceId,
        userRequest,
        r -> r.castToBigQueryDatasetResource().toApiResource(projectId));
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> updateBigQueryDataset(
      UUID workspaceId, UUID resourceId, ApiUpdateControlledResourceRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String projectId = workspaceService.getRequiredGcpProject(workspaceId);
    controlledResourceMetadataManager.updateControlledResourceMetadata(
        workspaceId, resourceId, body.getName(), body.getDescription(), userRequest);
    return controlledResourceToResponseEntity(
        workspaceId,
        resourceId,
        userRequest,
        r -> r.castToBigQueryDatasetResource().toApiResource(projectId));
  }

  /**
   * Utility function for retrieving and down-casting a controlled resource object
   *
   * @param workspaceId - ID of resource's workspace
   * @param resourceId - UUID for this controlled resource
   * @param userRequest - request object
   * @param converter - Function/lambda to convert from generic ControlledResource to appropriate
   *     Api resource type
   * @param <T> - Class to be converted to and type for the ResponseEntity
   * @return - response entity associated with this response object
   */
  private <T> ResponseEntity<T> controlledResourceToResponseEntity(
      UUID workspaceId,
      UUID resourceId,
      AuthenticatedUserRequest userRequest,
      Function<ControlledResource, T> converter) {
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    try {
      T response = converter.apply(controlledResource);
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (InvalidMetadataException ex) {
      throw new BadRequestException(
          String.format(
              "Resource %s in workspace %s is not a controlled BigQuery dataset.",
              resourceId, workspaceId));
    }
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpBigQueryDataset> createBigQueryDataset(
      UUID workspaceId, ApiCreateControlledGcpBigQueryDatasetRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    String projectId = workspaceService.getRequiredGcpProject(workspaceId);

    ControlledBigQueryDatasetResource resource =
        ControlledBigQueryDatasetResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(
                Optional.ofNullable(body.getCommon().getPrivateResourceUser())
                    .map(ApiPrivateResourceUser::getUserName)
                    .orElse(null))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .datasetName(body.getDataset().getDatasetId())
            .build();

    List<ControlledResourceIamRole> privateRoles = privateRolesFromBody(body.getCommon());

    final ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createBqDataset(resource, body.getDataset(), privateRoles, userRequest)
            .castToBigQueryDatasetResource();
    var response =
        new ApiCreatedControlledGcpBigQueryDataset()
            .resourceId(createdDataset.getResourceId())
            .bigQueryDataset(createdDataset.toApiResource(projectId));
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

    ValidationUtils.validate(body.getAiNotebookInstance());

    ControlledAiNotebookInstanceResource resource =
        ControlledAiNotebookInstanceResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(
                Optional.ofNullable(body.getCommon().getPrivateResourceUser())
                    .map(ApiPrivateResourceUser::getUserName)
                    .orElse(null))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .location(body.getAiNotebookInstance().getLocation())
            .instanceId(body.getAiNotebookInstance().getInstanceId())
            .build();

    List<ControlledResourceIamRole> privateRoles = privateRolesFromBody(body.getCommon());

    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            body.getAiNotebookInstance(),
            privateRoles,
            body.getJobControl(),
            ControllerUtils.getAsyncResultEndpoint(
                request, body.getJobControl().getId(), "create-result"),
            userRequest);

    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceCreateResult(jobId, userRequest);
    return new ResponseEntity<>(result, HttpStatus.valueOf(result.getJobReport().getStatusCode()));
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpAiNotebookInstanceResult>
      getCreateAiNotebookInstanceResult(UUID workspaceId, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceCreateResult(jobId, userRequest);
    return new ResponseEntity<>(result, HttpStatus.valueOf(result.getJobReport().getStatusCode()));
  }

  private ApiCreatedControlledGcpAiNotebookInstanceResult fetchNotebookInstanceCreateResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    AsyncJobResult<ControlledAiNotebookInstanceResource> jobResult =
        jobService.retrieveAsyncJobResult(
            jobId, ControlledAiNotebookInstanceResource.class, userRequest);

    ApiGcpAiNotebookInstanceResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      ControlledAiNotebookInstanceResource resource = jobResult.getResult();
      String workspaceProjectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
      apiResource = resource.toApiResource(workspaceProjectId);
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
            ControllerUtils.getAsyncResultEndpoint(request, jobControl.getId(), "delete-result"),
            userRequest);
    ApiDeleteControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceDeleteResult(jobId, userRequest);
    return new ResponseEntity<>(result, HttpStatus.valueOf(result.getJobReport().getStatusCode()));
  }

  @Override
  public ResponseEntity<ApiDeleteControlledGcpAiNotebookInstanceResult>
      getDeleteAiNotebookInstanceResult(UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiDeleteControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceDeleteResult(jobId, userRequest);
    return new ResponseEntity<>(result, HttpStatus.valueOf(result.getJobReport().getStatusCode()));
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
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    try {
      ApiGcpAiNotebookInstanceResource response =
          controlledResource
              .castToAiNotebookInstanceResource()
              .toApiResource(workspaceService.getRequiredGcpProject(workspaceId));
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (InvalidMetadataException ex) {
      throw new BadRequestException(
          String.format(
              "Resource %s in workspace %s is not a controlled AI Notebook Instance.",
              resourceId, workspaceId));
    }
  }

  /**
   * Extract a list of ControlledResourceIamRoles from the common fields of a controlled resource
   * request body, and validate that it's shaped appropriately for the specified AccessScopeType.
   *
   * <p>Shared access resources must not specify private resource roles. Private access resources
   * must specify at least one private resource role.
   */
  private List<ControlledResourceIamRole> privateRolesFromBody(
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
