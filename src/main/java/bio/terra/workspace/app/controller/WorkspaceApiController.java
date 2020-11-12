package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.model.Workspace;
import bio.terra.workspace.common.model.WorkspaceStage;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.generated.model.WorkspaceStageModel;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceService;
import java.util.Optional;
import java.util.UUID;
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
  public ResponseEntity<CreatedWorkspace> createWorkspace(
      @RequestBody CreateWorkspaceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    // Existing client libraries should not need to know about the stage, as they won't use any of
    // the features it gates. If stage isn't specified in a create request, we default to
    // RAWLS_WORKSPACE.
    WorkspaceStageModel requestStage = body.getStage();
    requestStage = (requestStage == null ? requestStage.RAWLS_WORKSPACE : requestStage);
    WorkspaceStage internalStage = WorkspaceStage.fromApiModel(requestStage);
    Optional<SpendProfileId> spendProfileId =
        Optional.ofNullable(body.getSpendProfile()).map(SpendProfileId::create);
    // If clients do not provide an operation ID, we generate one instead.
    String operationId =
        body.getOperationId() != null ? body.getOperationId() : UUID.randomUUID().toString();

    UUID createdId =
        workspaceService.createWorkspace(
            body.getId(), spendProfileId, internalStage, operationId, userReq);
    CreatedWorkspace responseWorkspace = new CreatedWorkspace().id(createdId);
    return new ResponseEntity<>(responseWorkspace, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<WorkspaceDescription> getWorkspace(@PathVariable("id") UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    Workspace workspace = workspaceService.getWorkspace(id, userReq);

    WorkspaceDescription desc =
        new WorkspaceDescription()
            .id(workspace.workspaceId())
            .spendProfile(workspace.spendProfileId().map(SpendProfileId::id).orElse(null))
            .stage(workspace.workspaceStage().toApiModel());

    return new ResponseEntity<>(desc, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteWorkspace(@PathVariable("id") UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    workspaceService.deleteWorkspace(id, userReq);
    return new ResponseEntity<>(HttpStatus.valueOf(204));
  }

  @Override
  public ResponseEntity<DataReferenceDescription> createDataReference(
      @PathVariable("id") UUID id, @RequestBody CreateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();

    return new ResponseEntity<DataReferenceDescription>(
        dataReferenceService.createDataReference(id, body, userReq), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReference(
      @PathVariable("id") UUID workspaceId, @PathVariable("referenceId") UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    DataReferenceDescription ref =
        dataReferenceService.getDataReference(workspaceId, referenceId, userReq);

    return new ResponseEntity<DataReferenceDescription>(ref, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReferenceByName(
      @PathVariable("id") UUID workspaceId,
      @PathVariable("referenceType") ReferenceTypeEnum referenceType,
      @PathVariable("name") String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    DataReferenceDescription ref =
        dataReferenceService.getDataReferenceByName(workspaceId, referenceType, name, userReq);

    return new ResponseEntity<DataReferenceDescription>(ref, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteDataReference(
      @PathVariable("id") UUID workspaceId, @PathVariable("referenceId") UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    dataReferenceService.deleteDataReference(workspaceId, referenceId, userReq);

    return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
  }

  /* Job endpoints disabled for now
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
  */

  @Override
  public ResponseEntity<DataReferenceList> enumerateReferences(
      @PathVariable("id") UUID id,
      @Valid @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
      @Valid @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
    ControllerValidationUtils.validatePaginationParams(offset, limit);
    DataReferenceList enumerateResult =
        dataReferenceService.enumerateDataReferences(id, offset, limit, getAuthenticatedInfo());
    return ResponseEntity.ok(enumerateResult);
  }
}
