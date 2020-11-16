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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private Logger logger = LoggerFactory.getLogger(WorkspaceApiController.class);

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<CreatedWorkspace> createWorkspace(
      @RequestBody CreateWorkspaceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(String.format("Creating workspace %s for %s", body.getId().toString(), userReq.getEmail()));

    // Existing client libraries should not need to know about the stage, as they won't use any of
    // the features it gates. If stage isn't specified in a create request, we default to
    // RAWLS_WORKSPACE.
    WorkspaceStageModel requestStage = body.getStage();
    requestStage = (requestStage == null ? requestStage.RAWLS_WORKSPACE : requestStage);
    WorkspaceStage internalStage = WorkspaceStage.fromApiModel(requestStage);
    Optional<SpendProfileId> spendProfileId =
        Optional.ofNullable(body.getSpendProfile()).map(SpendProfileId::create);

    UUID createdId =
        workspaceService.createWorkspace(body.getId(), spendProfileId, internalStage, userReq);
    CreatedWorkspace responseWorkspace = new CreatedWorkspace().id(createdId);
    logger.info(String.format("Created workspace %s for %s", responseWorkspace.toString(), userReq.getEmail()));

    return new ResponseEntity<>(responseWorkspace, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<WorkspaceDescription> getWorkspace(@PathVariable("id") UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(String.format("Getting workspace %s for %s", id.toString(), userReq.getEmail()));
    Workspace workspace = workspaceService.getWorkspace(id, userReq);
    WorkspaceDescription desc =
        new WorkspaceDescription()
            .id(workspace.workspaceId())
            .spendProfile(workspace.spendProfileId().map(SpendProfileId::id).orElse(null))
            .stage(workspace.workspaceStage().toApiModel());
    logger.info(String.format("Got workspace %s for %s", desc.toString(), userReq.getEmail()));

    return new ResponseEntity<>(desc, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteWorkspace(@PathVariable("id") UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(String.format("Deleting workspace %s for %s", id.toString(), userReq.getEmail()));
    workspaceService.deleteWorkspace(id, userReq);
    logger.info(String.format("Deleted workspace %s for %s", id.toString(), userReq.getEmail()));

    return new ResponseEntity<>(HttpStatus.valueOf(204));
  }

  @Override
  public ResponseEntity<DataReferenceDescription> createDataReference(
      @PathVariable("id") UUID id, @RequestBody CreateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(String.format("Creating data reference in workspace %s for %s with body %s",
            id.toString(), userReq.getEmail(), body.toString()));
    DataReferenceDescription desc = dataReferenceService.createDataReference(id, body, userReq);
    logger.info(String.format("Created data reference %s in workspace %s for %s ",
            desc.toString(), id.toString(), userReq.getEmail()));

    return new ResponseEntity<DataReferenceDescription>(desc, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReference(
      @PathVariable("id") UUID workspaceId, @PathVariable("referenceId") UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(String.format("Getting data reference by id %s in workspace %s for %s",
            referenceId.toString(), workspaceId.toString(), userReq.getEmail()));
    DataReferenceDescription ref =
        dataReferenceService.getDataReference(workspaceId, referenceId, userReq);
    logger.info(String.format("Got data reference %s in workspace %s for %s",
            ref.toString(), workspaceId.toString(), userReq.getEmail()));

    return new ResponseEntity<DataReferenceDescription>(ref, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReferenceByName(
      @PathVariable("id") UUID workspaceId,
      @PathVariable("referenceType") ReferenceTypeEnum referenceType,
      @PathVariable("name") String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(String.format("Getting data reference by name %s and reference type %s in workspace %s for %s",
            name, referenceType, workspaceId.toString(), userReq.getEmail()));
    DataReferenceDescription ref =
        dataReferenceService.getDataReferenceByName(workspaceId, referenceType, name, userReq);
    logger.info(String.format("Got data reference %s in workspace %s for %s",
            ref.toString(), referenceType, workspaceId.toString(), userReq.getEmail()));

    return new ResponseEntity<DataReferenceDescription>(ref, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteDataReference(
      @PathVariable("id") UUID workspaceId, @PathVariable("referenceId") UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(String.format("Deleting data reference by id %s in workspace %s for %s",
            referenceId.toString(), workspaceId.toString(), userReq.getEmail()));
    dataReferenceService.deleteDataReference(workspaceId, referenceId, userReq);
    logger.info(String.format("Deleted data reference by id %s in workspace %s for %s",
            referenceId.toString(), workspaceId.toString(), userReq.getEmail()));

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
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(String.format("Getting data references in workspace %s for %s",
            id.toString(), userReq.getEmail()));
    ControllerValidationUtils.validatePaginationParams(offset, limit);
    DataReferenceList enumerateResult =
        dataReferenceService.enumerateDataReferences(id, offset, limit, userReq);
    logger.info(String.format("Got data references in workspace %s for %s",
            enumerateResult.toString(), userReq.getEmail()));
    return ResponseEntity.ok(enumerateResult);
  }
}
