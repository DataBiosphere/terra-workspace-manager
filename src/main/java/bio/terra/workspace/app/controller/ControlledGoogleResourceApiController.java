package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ControlledGoogleResourceApi;
import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.generated.model.CreatedControlledGoogleBucket;
import bio.terra.workspace.generated.model.GoogleBucketStoredAttributes;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.resource.controlled.ControlledAccessType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
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
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    ControlledGcsBucketResource resource =
        new ControlledGcsBucketResource(
            workspaceId,
            UUID.randomUUID(), // mint the new resource id here
            body.getCommon().getName(),
            body.getCommon().getDescription(),
            CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()),
            body.getCommon().getPrivateResourceUser().getUserName(),
            ControlledAccessType.fromApi(
                body.getCommon().getAccessScope(), body.getCommon().getManagedBy()),
            body.getGoogleBucket().getName());

    final String jobId =
        controlledResourceService.createGcsBucket(
            resource,
            body.getGoogleBucket(),
            body.getCommon().getPrivateResourceUser().getIamRole(),
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
