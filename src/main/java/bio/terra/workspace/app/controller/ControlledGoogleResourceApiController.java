package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.ControlledGoogleResourceApi;
import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.generated.model.CreatedControlledGoogleBucket;
import bio.terra.workspace.generated.model.GoogleBucketStoredAttributes;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.resource.controlled.gcp.ControlledGcpResourceService;
import bio.terra.workspace.service.resource.controlled.gcp.ControlledGcsBucketResource;
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
  private final ControlledGcpResourceService controlledResourceService;
  private final HttpServletRequest request;
  private final JobService jobService;

  @Autowired
  public ControlledGoogleResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledGcpResourceService controlledResourceService,
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
    ControllerValidationUtils.validateGoogleBucket(body.getGoogleBucket());
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // TODO:store cloning instructions Should visible always default to true?
    final ControlledGcsBucketResource resource =
        new ControlledGcsBucketResource(
            body.getCommon().getName(),
            CloningInstructions.fromApiModel(body.getCommon().getCloningInstructions()),
            body.getCommon().getDescription(),
            workspaceId,
            body.getCommon().getOwner(),
            body.getGoogleBucket());
    final String jobId =
        controlledResourceService.createGcsBucket(
            resource, body.getCommon().getJobControl(), userRequest);
    return getCreateBucketResult(workspaceId, jobId);
  }

  @Override
  public ResponseEntity<CreatedControlledGoogleBucket> getCreateBucketResult(
      UUID id, String jobId) {
    final CreatedControlledGoogleBucket response = fetchGoogleBucketResult(jobId);
    return new ResponseEntity<>(
        response, HttpStatus.valueOf(response.getJobReport().getStatusCode()));
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
