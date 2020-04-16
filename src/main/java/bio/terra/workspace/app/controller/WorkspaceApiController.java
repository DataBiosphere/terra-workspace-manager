package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.JobResultWithStatus;
import bio.terra.workspace.service.workspace.WorkspaceService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WorkspaceApiController implements WorkspaceApi {
  private WorkspaceService workspaceService;
  private DataReferenceService dataReferenceService;
  private JobService jobService;
  private AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public WorkspaceApiController(
      WorkspaceService workspaceService,
      DataReferenceService dataReferenceService,
      JobService jobService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.workspaceService = workspaceService;
    this.dataReferenceService = dataReferenceService;
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
    workspaceService.createWorkspace(body, userReq);
    // Look up the newly-created job
    JobModel createJob = jobService.retrieveJob(body.getJobControl().getJobid(), userReq);
    return new ResponseEntity<JobModel>(createJob, HttpStatus.valueOf(createJob.getStatusCode()));
  }

  @Override
  public ResponseEntity<WorkspaceDescription> getWorkspace(@PathVariable("id") String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    WorkspaceDescription desc = workspaceService.getWorkspace(id, userReq);

    return new ResponseEntity<WorkspaceDescription>(desc, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<JobModel> createDataReference(
      @PathVariable("id") String id, @RequestBody CreateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    dataReferenceService.createDataReference(id, body, userReq);
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

  @Override
  public ResponseEntity<DataReferenceList> enumerateReferences(
      @PathVariable("id") String id,
      @Valid @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
      @Valid @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit,
      @Valid @RequestParam(value = "filterControlled", required = false, defaultValue = "all")
          String filterControlled) {
    ControllerValidationUtils.ValidatePaginationParams(offset, limit);
    ControllerValidationUtils.ValidateFilterParams(filterControlled);
    DataReferenceList enumerateResult =
        dataReferenceService.enumerateDataReferences(
            id, offset, limit, filterControlled, getAuthenticatedInfo());
    return ResponseEntity.ok(enumerateResult);
  }
}
