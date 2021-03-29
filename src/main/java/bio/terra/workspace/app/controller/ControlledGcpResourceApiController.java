package bio.terra.workspace.app.controller;

import static java.util.stream.Collectors.toCollection;

import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.generated.controller.ControlledGcpResourceApi;
import bio.terra.workspace.generated.model.ApiCreateControlledGcsBucketRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcsBucket;
import bio.terra.workspace.generated.model.ApiGcsBucketStoredAttributes;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRoleList;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.ArrayList;
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

    // This value is serialized in Stairway, so we avoid making a generic list.
    ControlledResourceIamRoleList privateRoles =
        (ControlledResourceIamRoleList)
            Optional.ofNullable(body.getCommon().getPrivateResourceUser())
                .map(
                    user ->
                        user.getPrivateResourceIamRoles().stream()
                            .map(ControlledResourceIamRole::fromApiModel)
                            .collect(toCollection(ArrayList::new)))
                .orElse(null);
    if (privateRoles != null && privateRoles.isEmpty()) {
      throw new ValidationException(
          "At least one IAM role is required for private resources and the field must be omitted for shared resources");
    }

    final String jobId =
        controlledResourceService.createControlledResource(
            resource,
            body.getGcsBucket(),
            privateRoles,
            body.getCommon().getJobControl(),
            userRequest);
    return getCreateBucketResult(workspaceId, jobId);
  }

  @Override
  public ResponseEntity<ApiCreatedControlledGcsBucket> getCreateBucketResult(
      UUID id, String jobId) {
    final ApiCreatedControlledGcsBucket response = fetchGcsBucketResult(jobId);
    return new ResponseEntity<>(
        response, HttpStatus.valueOf(response.getJobReport().getStatusCode()));
  }

  @Override
  public ResponseEntity<ApiGcsBucketStoredAttributes> getBucket(UUID id, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(id, resourceId, userRequest);
    ApiGcsBucketStoredAttributes response =
        controlledResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private ApiCreatedControlledGcsBucket fetchGcsBucketResult(String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final AsyncJobResult<ApiGcsBucketStoredAttributes> jobResult =
        jobService.retrieveAsyncJobResult(jobId, ApiGcsBucketStoredAttributes.class, userRequest);
    return new ApiCreatedControlledGcsBucket()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .gcpBucket(jobResult.getResult());
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
