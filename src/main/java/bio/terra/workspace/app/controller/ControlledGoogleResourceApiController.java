package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ControlledGoogleResourceApi;
import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.generated.model.CreatedControlledGoogleBucket;
import bio.terra.workspace.generated.model.GoogleBucketStoredAttributes;
import bio.terra.workspace.generated.model.PrivateResourceUser;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
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
public class ControlledGoogleResourceApiController implements ControlledGoogleResourceApi {
  private final Logger logger =
      LoggerFactory.getLogger(ControlledGoogleResourceApiController.class);

  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final ControlledResourceService controlledResourceService;
  private final HttpServletRequest request;
  private final JobService jobService;

  @Autowired
  public ControlledGoogleResourceApiController(
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
  public ResponseEntity<CreatedControlledGoogleBucket> createBucket(
      UUID workspaceId, @Valid CreateControlledGoogleBucketRequestBody body) {
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
                    .map(PrivateResourceUser::getUserName)
                    .orElse(null))
            .accessScope(AccessScopeType.fromApi(body.getCommon().getAccessScope()))
            .managedBy(ManagedByType.fromApi(body.getCommon().getManagedBy()))
            .bucketName(body.getGoogleBucket().getName())
            .build();

    final String jobId =
        controlledResourceService.createControlledResource(
            resource,
            body.getGoogleBucket(),
            Optional.ofNullable(body.getCommon().getPrivateResourceUser())
                .map(PrivateResourceUser::getIamRole)
                .orElse(null),
            body.getCommon().getJobControl(),
            userRequest);
    return getCreateBucketResult(workspaceId, jobId);
  }

  @Override
  public ResponseEntity<CreatedControlledGoogleBucket> getCreateBucketResult(
      UUID id, String jobId) {
    final CreatedControlledGoogleBucket response = fetchGoogleBucketResult(jobId);
    return new ResponseEntity<>(
        response, HttpStatus.valueOf(response.getJobReport().getStatusCode()));
  }

  @Override
  public ResponseEntity<GoogleBucketStoredAttributes> getBucket(UUID id, UUID resourceId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControlledResource controlledResource =
        controlledResourceService.getControlledResource(id, resourceId, userRequest);
    GoogleBucketStoredAttributes response =
        controlledResource.castToGcsBucketResource().toApiModel();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private CreatedControlledGoogleBucket fetchGoogleBucketResult(String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final AsyncJobResult<GoogleBucketStoredAttributes> jobResult =
        jobService.retrieveAsyncJobResult(jobId, GoogleBucketStoredAttributes.class, userRequest);
    return new CreatedControlledGoogleBucket()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getErrorReport())
        .googleBucket(jobResult.getResult());
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
