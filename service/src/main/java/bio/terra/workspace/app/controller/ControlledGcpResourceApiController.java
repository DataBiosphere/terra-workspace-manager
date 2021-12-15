package bio.terra.workspace.app.controller;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.ControllerUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.generated.controller.ControlledGcpResourceApi;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpBigQueryDatasetResult;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiCloneControlledGcpGcsBucketResult;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
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
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpBigQueryDatasetRequestBody;
import bio.terra.workspace.generated.model.ApiUpdateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.controlled.PrivateUserRole;
import bio.terra.workspace.service.resource.controlled.exception.InvalidControlledResourceException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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
public class ControlledGcpResourceApiController implements ControlledGcpResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledGcpResourceApiController.class);

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final ControlledResourceService controlledResourceService;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final HttpServletRequest request;
  private final JobService jobService;

  @Autowired
  public ControlledGcpResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledResourceService controlledResourceService,
      SamService samService,
      WorkspaceService workspaceService,
      JobService jobService,
      HttpServletRequest request) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.controlledResourceService = controlledResourceService;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.request = request;
    this.jobService = jobService;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpGcsBucket> createBucket(
      UUID workspaceId, @Valid ApiCreateControlledGcpGcsBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, body.getCommon(), userRequest);

    ManagedByType managedBy = ManagedByType.fromApi(body.getCommon().getManagedBy());

    ControlledGcsBucketResource resource =
        ControlledGcsBucketResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(privateUserRole.getUserEmail())
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(managedBy)
            .applicationId(controlledResourceService.getAssociatedApp(managedBy, userRequest))
            .bucketName(body.getGcsBucket().getName())
            .build();

    final ControlledGcsBucketResource createdBucket =
        controlledResourceService.createBucket(
            resource, body.getGcsBucket(), privateUserRole.getRole(), userRequest);
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
        response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
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
    final ControlledResource resource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    if (resource.getResourceType() != WsmResourceType.GCS_BUCKET) {
      throw new InvalidControlledResourceException(
          String.format("Resource %s is not a GCS Bucket", resourceId));
    }
    final ControlledGcsBucketResource bucketResource = resource.castToGcsBucketResource();
    controlledResourceService.updateGcsBucket(
        bucketResource,
        body.getUpdateParameters(),
        userRequest,
        body.getName(),
        body.getDescription());

    // Retrieve and cast response to ApiGcpGcsBucketResource
    return getControlledResourceAsResponseEntity(
        workspaceId, resourceId, userRequest, r -> r.castToGcsBucketResource().toApiResource());
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpGcsBucketResult> cloneGcsBucket(
      UUID workspaceId, UUID resourceId, @Valid ApiCloneControlledGcpGcsBucketRequest body) {
    logger.info("Cloning GCS bucket resourceId {} workspaceId {}", resourceId, workspaceId);

    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final String jobId =
        controlledResourceService.cloneGcsBucket(
            workspaceId,
            resourceId,
            body.getDestinationWorkspaceId(),
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getBucketName(),
            body.getLocation(),
            body.getCloningInstructions());
    final ApiCloneControlledGcpGcsBucketResult result =
        fetchCloneGcsBucketResult(jobId, userRequest);
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
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
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> getBigQueryDataset(
      UUID workspaceId, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String projectId = workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest);
    return getControlledResourceAsResponseEntity(
        workspaceId,
        resourceId,
        userRequest,
        r -> r.castToBigQueryDatasetResource().toApiResource(projectId));
  }

  @Override
  public ResponseEntity<ApiGcpBigQueryDatasetResource> updateBigQueryDataset(
      UUID workspaceId, UUID resourceId, ApiUpdateControlledGcpBigQueryDatasetRequestBody body) {
    logger.info("Updating dataset resourceId {} workspaceId {}", resourceId, workspaceId);
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ControlledResource resource =
        controlledResourceService.getControlledResource(workspaceId, resourceId, userRequest);
    if (resource.getResourceType() != WsmResourceType.BIG_QUERY_DATASET) {
      throw new InvalidControlledResourceException(
          String.format("Resource %s is not a BigQuery Dataset", resourceId));
    }
    final ControlledBigQueryDatasetResource datasetResource =
        resource.castToBigQueryDatasetResource();
    controlledResourceService.updateBqDataset(
        datasetResource,
        body.getUpdateParameters(),
        userRequest,
        body.getName(),
        body.getDescription());

    // Retrieve and cast response to UpdateControlledGcpBigQueryDatasetResponse
    String projectId = workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest);
    return getControlledResourceAsResponseEntity(
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
  private <T> ResponseEntity<T> getControlledResourceAsResponseEntity(
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

    String projectId = workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest);

    PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, body.getCommon(), userRequest);

    ManagedByType managedBy = ManagedByType.fromApi(body.getCommon().getManagedBy());

    ControlledBigQueryDatasetResource resource =
        ControlledBigQueryDatasetResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(privateUserRole.getUserEmail())
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(managedBy)
            .applicationId(controlledResourceService.getAssociatedApp(managedBy, userRequest))
            .datasetName(
                Optional.ofNullable(body.getDataset().getDatasetId())
                    .orElse(body.getCommon().getName()))
            .build();

    final ControlledBigQueryDatasetResource createdDataset =
        controlledResourceService
            .createBigQueryDataset(
                resource, body.getDataset(), privateUserRole.getRole(), userRequest)
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

    PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, body.getCommon(), userRequest);

    ManagedByType managedBy = ManagedByType.fromApi(body.getCommon().getManagedBy());

    ControlledAiNotebookInstanceResource resource =
        ControlledAiNotebookInstanceResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name(body.getCommon().getName())
            .description(body.getCommon().getDescription())
            .cloningInstructions(
                CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()))
            .assignedUser(privateUserRole.getUserEmail())
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(managedBy)
            .applicationId(controlledResourceService.getAssociatedApp(managedBy, userRequest))
            .location(body.getAiNotebookInstance().getLocation())
            .instanceId(
                Optional.ofNullable(body.getAiNotebookInstance().getInstanceId())
                    .orElse(body.getCommon().getName()))
            .build();

    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            body.getAiNotebookInstance(),
            privateUserRole.getRole(),
            body.getJobControl(),
            ControllerUtils.getAsyncResultEndpoint(
                request, body.getJobControl().getId(), "create-result"),
            userRequest);

    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceCreateResult(jobId, userRequest);
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode((result.getJobReport())));
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcpAiNotebookInstanceResult>
      getCreateAiNotebookInstanceResult(UUID workspaceId, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiCreatedControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceCreateResult(jobId, userRequest);
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCreatedControlledGcpAiNotebookInstanceResult fetchNotebookInstanceCreateResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    AsyncJobResult<ControlledAiNotebookInstanceResource> jobResult =
        jobService.retrieveAsyncJobResult(
            jobId, ControlledAiNotebookInstanceResource.class, userRequest);

    ApiGcpAiNotebookInstanceResource apiResource = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      ControlledAiNotebookInstanceResource resource = jobResult.getResult();
      String workspaceProjectId =
          workspaceService.getAuthorizedRequiredGcpProject(resource.getWorkspaceId(), userRequest);
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
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiDeleteControlledGcpAiNotebookInstanceResult>
      getDeleteAiNotebookInstanceResult(UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiDeleteControlledGcpAiNotebookInstanceResult result =
        fetchNotebookInstanceDeleteResult(jobId, userRequest);
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
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
              .toApiResource(
                  workspaceService.getAuthorizedRequiredGcpProject(workspaceId, userRequest));
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (InvalidMetadataException ex) {
      throw new BadRequestException(
          String.format(
              "Resource %s in workspace %s is not a controlled AI Notebook Instance.",
              resourceId, workspaceId));
    }
  }

  @Override
  public ResponseEntity<ApiCloneControlledGcpBigQueryDatasetResult> cloneBigQueryDataset(
      UUID workspaceId, UUID resourceId, @Valid ApiCloneControlledGcpBigQueryDatasetRequest body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final String jobId =
        controlledResourceService.cloneBigQueryDataset(
            workspaceId,
            resourceId,
            body.getDestinationWorkspaceId(),
            body.getJobControl(),
            userRequest,
            body.getName(),
            body.getDescription(),
            body.getDestinationDatasetName(),
            body.getLocation(),
            body.getCloningInstructions());
    final ApiCloneControlledGcpBigQueryDatasetResult result =
        fetchCloneBigQueryDatasetResult(jobId, userRequest);
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
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
  }

  /**
   * Validate and provide defaulting for the private resource user. The property is never required.
   * The only time it is allowed is for application-private resources. If it is populated, we
   * validate the user email and the specified IAM roles.
   *
   * <p>user-private resources are always assigned to the caller. You can't create a user-private
   * resource and assign it to someone else. Because we can read the caller's email from the
   * AuthenticatedUserRequest, we don't need to supply assignedUser in the request body.
   *
   * <p>application-private resources can be assigned to users other than the caller. For example,
   * Leo could call WSM to create a VM (using the Leo SA's auth token) and request it be assigned to
   * user X, not to the Leo SA.
   *
   * @param commonFields common fields from a controlled resource create request
   * @param userRequest authenticate user
   * @return PrivateUserRole holding the user email and the role list
   */
  private PrivateUserRole computePrivateUserRole(
      UUID workspaceId,
      ApiControlledResourceCommonFields commonFields,
      AuthenticatedUserRequest userRequest) {

    AccessScopeType accessScope = AccessScopeType.fromApi(commonFields.getAccessScope());
    ManagedByType managedBy = ManagedByType.fromApi(commonFields.getManagedBy());
    ApiPrivateResourceUser inputUser = commonFields.getPrivateResourceUser();

    // Shared access has no private user role
    if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED) {
      validateNoInputUser(inputUser);
      return new PrivateUserRole.Builder().present(false).build();
    }

    // Private access scope
    switch (managedBy) {
      case MANAGED_BY_APPLICATION:
        {
          // Supplying a user is optional for applications
          if (inputUser == null) {
            return new PrivateUserRole.Builder().present(false).build();
          }

          // We have a private user, so make sure the email is present and valid
          String userEmail = commonFields.getPrivateResourceUser().getUserName();
          ControllerValidationUtils.validateEmail(userEmail);

          // Validate that the assigned user is a member of the workspace. It must have at least
          // READ action.
          SamRethrow.onInterrupted(
              () ->
                  samService.userIsAuthorized(
                      SamConstants.SamResource.WORKSPACE,
                      workspaceId.toString(),
                      SamConstants.SamWorkspaceAction.READ,
                      userEmail,
                      userRequest),
              "validate private user is workspace member");

          // Translate the incoming role list into our internal model form
          // This also validates that the incoming API model values are correct.
          List<ControlledResourceIamRole> roles =
              commonFields.getPrivateResourceUser().getPrivateResourceIamRoles().stream()
                  .map(ControlledResourceIamRole::fromApiModel)
                  .collect(Collectors.toList());
          if (roles.isEmpty()) {
            throw new ValidationException(
                "You must specify at least one role when you specify PrivateResourceIamRoles");
          }

          // The legal options for the assigned user of an application is READER
          // or WRITER. EDITOR is not allowed. We take the "max" of READER and WRITER.
          var maxRole = ControlledResourceIamRole.READER;
          for (ControlledResourceIamRole role : roles) {
            if (role == ControlledResourceIamRole.WRITER) {
              if (maxRole == ControlledResourceIamRole.READER) {
                maxRole = role;
              }
            } else if (role != ControlledResourceIamRole.READER) {
              throw new ValidationException(
                  "For application private controlled resources, only READER and WRITER roles are allowed. Found "
                      + role.toApiModel());
            }
          }
          return new PrivateUserRole.Builder()
              .present(true)
              .userEmail(userEmail)
              .role(maxRole)
              .build();
        }

      case MANAGED_BY_USER:
        {
          // TODO: PF-1218 The target state is that supplying a user is not allowed.
          //  However, current CLI and maybe UI are supplying all or part of the structure,
          //  so tolerate all states: no-input, only roles, roles and user
          /* Target state:
          // Supplying a user is not allowed. The creating user is always the assigned user.
          validateNoInputUser(inputUser);
          */

          // Fill in the user role for the creating user
          String userEmail =
              SamRethrow.onInterrupted(
                  () -> samService.getUserEmailFromSam(userRequest), "getUserEmailFromSam");

          // TODO: PF-1218 temporarily allow user spec and make sure it matches the requesting
          //  user. Ignore the role list. If the user name is specified, then make sure it
          //  matches the requesting name.
          if (inputUser != null && inputUser.getUserName() != null) {
            if (!StringUtils.equalsIgnoreCase(userEmail, inputUser.getUserName())) {
              throw new BadRequestException(
                  "User ("
                      + userEmail
                      + ") may only assign a private controlled resource to themselves");
            }
          }

          // At this time, all private resources grant EDITOR permission to the resource user.
          // This could be parameterized if we ever have reason to grant different permissions
          // to different objects.
          return new PrivateUserRole.Builder()
              .present(true)
              .userEmail(userEmail)
              .role(ControlledResourceIamRole.EDITOR)
              .build();
        }

      default:
        throw new InternalLogicException("Unknown managedBy enum");
    }
  }

  private void validateNoInputUser(@Nullable ApiPrivateResourceUser inputUser) {
    if (inputUser != null) {
      throw new ValidationException(
          "PrivateResourceUser can only be specified by applications for private resources");
    }
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
