package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.datareference.create.CreateDataReferenceService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.JobResultWithStatus;
import bio.terra.workspace.service.workspace.create.CreateService;
import bio.terra.workspace.service.workspace.get.GetService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class WorkspaceApiController implements WorkspaceApi {
  private CreateService createService;
  private CreateDataReferenceService createDataReferenceService;
  private GetService getService;
  private JobService jobService;
  private AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public WorkspaceApiController(
      CreateService createService,
      CreateDataReferenceService createDataReferenceService,
      GetService getService,
      JobService jobService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.createService = createService;
    this.getService = getService;
    this.jobService = jobService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<JobModel> createWorkspace(@RequestBody CreateWorkspaceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    createService.createWorkspace(body, userReq);
    // Look up the newly-created job
    JobModel createJob = jobService.retrieveJob(body.getJobControl().getJobid(), userReq);
    return new ResponseEntity<JobModel>(createJob, HttpStatus.valueOf(createJob.getStatusCode()));
  }

  @Override
  public ResponseEntity<WorkspaceDescription> getWorkspace(@PathVariable("id") String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    WorkspaceDescription desc = getService.getWorkspace(id, userReq);

    return new ResponseEntity<WorkspaceDescription>(desc, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<JobModel> createDataReference(
      @PathVariable("id") String id, @RequestBody CreateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    createDataReferenceService.createDataReference(id, body, userReq);
    // Look up the newly-created job
    JobModel createJob = jobService.retrieveJob(body.getJobControl().getJobid(), userReq);
    return new ResponseEntity<JobModel>(createJob, HttpStatus.valueOf(createJob.getStatusCode()));
  }

  @Override
  public ResponseEntity<Void> deleteJob(@PathVariable("id") String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    jobService.releaseJob(id, userReq);
    return new ResponseEntity<>(HttpStatus.valueOf(204));
  }

  @Override
  public ResponseEntity<JobModel> pollAsyncJob(@PathVariable("id") String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    JobModel job = jobService.retrieveJob(id, userReq);
    return new ResponseEntity<JobModel>(job, HttpStatus.valueOf(job.getStatusCode()));
  }

  @Override
  public ResponseEntity<Object> retrieveJobResult(@PathVariable("id") String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    JobResultWithStatus<Object> jobResultHolder =
        jobService.retrieveJobResult(id, Object.class, userReq);
    return new ResponseEntity<>(jobResultHolder.getResult(), jobResultHolder.getStatusCode());
  }
}
