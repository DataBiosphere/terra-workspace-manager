package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ControlledGcpResourceApi;
import bio.terra.workspace.generated.model.ApiCreateControlledGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcsBucket;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcsBucketRequest;
import bio.terra.workspace.generated.model.ApiDeleteControlledGcsBucketResult;
import bio.terra.workspace.generated.model.ApiGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiJobControl;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
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
public class ControlledGcpResourceApiController implements ControlledGcpResourceApi {
  private final Logger logger = LoggerFactory.getLogger(ControlledGcpResourceApiController.class);

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final ControlledResourceService controlledResourceService;
  private final HttpServletRequest request;
  private final JobService jobService;

  @Autowired
  public ControlledGcpResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledResourceService controlledResourceService,
      JobService jobService,
      HttpServletRequest request) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.controlledResourceService = controlledResourceService;
    this.request = request;
    this.jobService = jobService;
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcsBucket> createBucket(
      UUID workspaceId, @Valid ApiCreateControlledGcsBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledGcsBucketResource resource =
        ControlledGcsBucketResource.builder()
            .workspaceId(workspaceId)
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

    final String jobId =
        controlledResourceService.createControlledResource(
            resource,
            body.getGcsBucket(),
            Optional.ofNullable(body.getCommon().getPrivateResourceUser())
                .map(apiUser -> WsmIamRole.fromApiModel(apiUser.getIamRole()))
                .orElse(null),
            body.getCommon().getJobControl(),
            userRequest);
    return getCreateBucketResult(workspaceId, jobId);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledGcsBucketResult> deleteBucket(
      UUID workspaceId, UUID resourceId, @Valid ApiDeleteControlledGcsBucketRequest body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final ApiJobControl jobControl = body.getJobControl();
    logger.info(
        "deleteBucket workspace {} resource {}", workspaceId.toString(), resourceId.toString());
    final String jobId =
        controlledResourceService.deleteControlledGcsBucket(
            jobControl, workspaceId, resourceId, userRequest);
    return getDeleteResult(jobId, userRequest);
  }

  @Override
  public ResponseEntity<ApiDeleteControlledGcsBucketResult> getDeleteBucketResult(
      UUID workspaceId, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return getDeleteResult(jobId, userRequest);
  }

  private ResponseEntity<ApiDeleteControlledGcsBucketResult> getDeleteResult(
      String jobId, AuthenticatedUserRequest userRequest) {
    final AsyncJobResult<Void> jobResult =
        jobService.retrieveAsyncJobResult(jobId, Void.class, userRequest);
    var response =
        new ApiDeleteControlledGcsBucketResult()
            .jobReport(jobResult.getJobReport())
            .errorReport(jobResult.getApiErrorReport());
    return new ResponseEntity<>(
        response, HttpStatus.valueOf(response.getJobReport().getStatusCode()));
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcsBucket> getCreateBucketResult(
      UUID id, String jobId) {
    final ApiCreatedControlledGcsBucket response = fetchGcsBucketResult(jobId);
    return new ResponseEntity<>(
        response, HttpStatus.valueOf(response.getJobReport().getStatusCode()));
  }

  @Override
  public ResponseEntity<ApiGcsBucketAttributes> getBucket(UUID id, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(id, resourceId, userRequest);
    ApiGcsBucketAttributes response = controlledResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private ApiCreatedControlledGcsBucket fetchGcsBucketResult(String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final AsyncJobResult<ControlledResource> jobResult =
        jobService.retrieveAsyncJobResult(jobId, ControlledResource.class, userRequest);

    ControlledResource resource = jobResult.getResult();
    UUID resourceId = Optional.ofNullable(resource).map(WsmResource::getResourceId).orElse(null);
    ApiGcsBucketAttributes gcpBucket =
        Optional.ofNullable(resource)
            .map(r -> r.castToGcsBucketResource().toApiModel())
            .orElse(null);

    return new ApiCreatedControlledGcsBucket()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .resourceId(resourceId)
        .gcpBucket(gcpBucket);
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
