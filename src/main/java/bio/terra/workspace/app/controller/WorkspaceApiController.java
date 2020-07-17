package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.JobResultWithStatus;
import bio.terra.workspace.service.trace.StackdriverTrace;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.Optional;
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
  // private final StackdriverTrace trace;

  @Autowired
  public WorkspaceApiController(
      WorkspaceService workspaceService,
      DataReferenceService dataReferenceService,
      JobService jobService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      StackdriverTrace trace) {
    this.workspaceService = workspaceService;
    this.dataReferenceService = dataReferenceService;
    this.jobService = jobService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
    //  this.trace = trace;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  // private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Override
  public ResponseEntity<CreatedWorkspace> createWorkspace(
      @RequestBody CreateWorkspaceRequestBody body) {
    // Note: we do NOT use getAuthenticatedInfo here, as the request's authentication info comes
    // from the folder manager, not the requesting user.
    String userToken = body.getAuthToken();
    AuthenticatedUserRequest userReq = new AuthenticatedUserRequest().token(Optional.of(userToken));
    return new ResponseEntity<>(workspaceService.createWorkspace(body, userReq), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<WorkspaceDescription> getWorkspace(@PathVariable("id") String id) {
    // try (Scope s = trace.scope(request.getRequestURI())) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    WorkspaceDescription desc = workspaceService.getWorkspace(id, userReq);

    return new ResponseEntity<WorkspaceDescription>(desc, HttpStatus.OK);
    // }
  }

  @Override
  public ResponseEntity<Void> deleteWorkspace(
      DeleteWorkspaceRequestBody body, @PathVariable("id") String id) {
    // Note: we do NOT use getAuthenticatedInfo here, as the request's authentication info comes
    // from the folder manager, not the requesting user.
    String userToken = body.getAuthToken();
    workspaceService.deleteWorkspace(id, userToken);
    return new ResponseEntity<>(HttpStatus.valueOf(204));
  }

  @Override
  public ResponseEntity<DataReferenceDescription> createDataReference(
      @RequestBody CreateDataReferenceRequestBody body, @PathVariable("id") String id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();

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
  public ResponseEntity<DataReferenceDescription> getDataReferenceByName(
      @PathVariable("id") String workspaceId,
      @PathVariable("referenceType") String referenceType,
      @PathVariable("name") String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    DataReferenceDescription ref =
        dataReferenceService.getDataReferenceByName(workspaceId, referenceType, name, userReq);

    return new ResponseEntity<DataReferenceDescription>(ref, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteDataReference(
      @PathVariable("id") String workspaceId, @PathVariable("referenceId") String referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    dataReferenceService.deleteDataReference(workspaceId, referenceId, userReq);

    return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
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
      @Valid @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
    ControllerValidationUtils.validatePaginationParams(offset, limit);
    DataReferenceList enumerateResult =
        dataReferenceService.enumerateDataReferences(id, offset, limit, getAuthenticatedInfo());
    return ResponseEntity.ok(enumerateResult);
  }
}
