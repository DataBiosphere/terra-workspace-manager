package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.ControlledGoogleResourceApi;
import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.generated.model.CreatedControlledGoogleBucket;
import bio.terra.workspace.service.controlledresource.ControlledGoogleResourceService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
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
  private final ControlledGoogleResourceService controlledResourceService;
  private final HttpServletRequest request;
  private final JobService jobService;

  @Autowired
  public ControlledGoogleResourceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      ControlledGoogleResourceService controlledResourceService,
      HttpServletRequest request,
      JobService jobService) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.controlledResourceService = controlledResourceService;
    this.request = request;
    this.jobService = jobService;
  }

  @Override
  public ResponseEntity<CreatedControlledGoogleBucket> createBucket(
      @Valid CreateControlledGoogleBucketRequestBody body) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final String jobId = controlledResourceService.createBucket(body, userRequest);
    final CreatedControlledGoogleBucket result = fetchGoogleBucketResult(jobId);
    return new ResponseEntity<>(result, HttpStatus.valueOf(result.getJobReport().getStatusCode()))
  }

  @Override
  public ResponseEntity<CreatedControlledGoogleBucket> getCreateBucketResult(String jobId) {
    final CreatedControlledGoogleBucket response = fetchGoogleBucketResult(jobId);
    return new ResponseEntity<>(response, HttpStatus.valueOf(response.getJobReport().getStatusCode()))
  }

  private CreatedControlledGoogleBucket fetchGoogleBucketResult(String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    final AsyncJobResult<CreatedControlledGoogleBucket> jobResult =
        jobService.retrieveAsyncJobResult(jobId, CreatedControlledGoogleBucket.class, userRequest);

  }
  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }
}
