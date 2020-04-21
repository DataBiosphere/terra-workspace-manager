package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.*;
<<<<<<< HEAD
import bio.terra.workspace.service.create.CreateService;
import bio.terra.workspace.service.get.GetService;
=======
import bio.terra.workspace.service.datareference.DataReferenceService;
>>>>>>> add uncontrolled resource endpoints
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.JobResultWithStatus;
<<<<<<< HEAD
=======
import bio.terra.workspace.service.workspace.create.CreateService;
import bio.terra.workspace.service.workspace.get.GetService;
>>>>>>> add uncontrolled resource endpoints
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
  private GetService getService;
<<<<<<< HEAD
=======
  private DataReferenceService dataReferenceService;
>>>>>>> add uncontrolled resource endpoints
  private JobService jobService;
  private AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public WorkspaceApiController(
      CreateService createService,
      GetService getService,
<<<<<<< HEAD
=======
      DataReferenceService dataReferenceService,
>>>>>>> add uncontrolled resource endpoints
      JobService jobService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.createService = createService;
<<<<<<< HEAD
=======
    this.dataReferenceService = dataReferenceService;
>>>>>>> add uncontrolled resource endpoints
    this.getService = getService;
    this.jobService = jobService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<CreatedWorkspace> createWorkspace(
      @RequestBody CreateWorkspaceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    return new ResponseEntity<>(createService.createWorkspace(body, userReq), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<WorkspaceDescription> getWorkspace(@PathVariable("id") String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    WorkspaceDescription desc = getService.getWorkspace(id, userReq);

    return new ResponseEntity<WorkspaceDescription>(desc, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> createDataReference(
      @PathVariable("id") String id, @RequestBody CreateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    dataReferenceService.createDataReference(id, body, userReq);

    return new ResponseEntity<DataReferenceDescription>(
        dataReferenceService.createDataReference(id, body, userReq), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReference(
      @PathVariable("id") String workspaceId, @PathVariable("referenceId") String referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    DataReferenceDescription ref =
        dataReferenceService.getDataReference(workspaceId, referenceId, userReq);

    return new ResponseEntity<DataReferenceDescription>(ref, HttpStatus.OK);
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
