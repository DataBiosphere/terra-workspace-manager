package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.CloudContext;
import bio.terra.workspace.generated.model.CreateCloudContextRequest;
import bio.terra.workspace.generated.model.CreateCloudContextResult;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.GcpContext;
import bio.terra.workspace.generated.model.GrantRoleRequestBody;
import bio.terra.workspace.generated.model.IamRole;
import bio.terra.workspace.generated.model.JobReport.StatusEnum;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.generated.model.RoleBindingList;
import bio.terra.workspace.generated.model.UpdateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.generated.model.WorkspaceStageModel;
import bio.terra.workspace.service.datareference.DataReferenceService;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datareference.utils.DataReferenceValidationUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.List;
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
  private final WorkspaceService workspaceService;
  private final DataReferenceService dataReferenceService;
  private final DataReferenceValidationUtils dataReferenceValidation;
  private final JobService jobService;
  private final SamService samService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;

  @Autowired
  public WorkspaceApiController(
      WorkspaceService workspaceService,
      DataReferenceService dataReferenceService,
      DataReferenceValidationUtils dataReferenceValidation,
      JobService jobService,
      SamService samService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request) {
    this.workspaceService = workspaceService;
    this.dataReferenceService = dataReferenceService;
    this.dataReferenceValidation = dataReferenceValidation;
    this.jobService = jobService;
    this.samService = samService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
  }

  private final Logger logger = LoggerFactory.getLogger(WorkspaceApiController.class);

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  // Returns the result endpoint corresponding to an async request, prefixed with a / character.
  // Used to build a JobReport. This assumes the result endpoint is at /result/{jobId} relative to
  // the async endpoint, which is standard but not enforced.
  private String getAsyncResultEndpoint(String jobId) {
    return String.format("%s/result/%s", request.getServletPath(), jobId);
  }

  @Override
  public ResponseEntity<CreatedWorkspace> createWorkspace(
      @RequestBody CreateWorkspaceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info("Creating workspace {} for {}", body.getId(), userReq.getEmail());

    // Existing client libraries should not need to know about the stage, as they won't use any of
    // the features it gates. If stage isn't specified in a create request, we default to
    // RAWLS_WORKSPACE.
    WorkspaceStageModel requestStage = body.getStage();
    requestStage = (requestStage == null ? WorkspaceStageModel.RAWLS_WORKSPACE : requestStage);
    WorkspaceStage internalStage = WorkspaceStage.fromApiModel(requestStage);
    Optional<SpendProfileId> spendProfileId =
        Optional.ofNullable(body.getSpendProfile()).map(SpendProfileId::create);
    // If clients do not provide a job ID, we generate one instead.
    String jobId = body.getJobId() != null ? body.getJobId() : UUID.randomUUID().toString();

    WorkspaceRequest internalRequest =
        WorkspaceRequest.builder()
            .workspaceId(body.getId())
            .jobId(jobId)
            .spendProfileId(spendProfileId)
            .workspaceStage(internalStage)
            .build();
    UUID createdId = workspaceService.createWorkspace(internalRequest, userReq);

    CreatedWorkspace responseWorkspace = new CreatedWorkspace().id(createdId);
    logger.info("Created workspace {} for {}", responseWorkspace, userReq.getEmail());

    return new ResponseEntity<>(responseWorkspace, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<WorkspaceDescription> getWorkspace(@PathVariable("id") UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info("Getting workspace {} for {}", id, userReq.getEmail());
    Workspace workspace = workspaceService.getWorkspace(id, userReq);
    GcpContext gcpContext =
        workspace
            .getGcpCloudContext()
            .map(g -> new GcpContext().projectId(g.getGcpProjectId()))
            .orElse(null);

    // Note projectId will be null here if no GCP cloud context exists.
    // When we have another cloud context, we will need to do a similar retrieval for it.
    WorkspaceDescription desc =
        new WorkspaceDescription()
            .id(workspace.getWorkspaceId())
            .spendProfile(workspace.getSpendProfileId().map(SpendProfileId::id).orElse(null))
            .stage(workspace.getWorkspaceStage().toApiModel())
            .gcpContext(gcpContext);
    logger.info("Got workspace {} for {}", desc, userReq.getEmail());

    return new ResponseEntity<>(desc, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteWorkspace(@PathVariable("id") UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info("Deleting workspace {} for {}", id, userReq.getEmail());
    workspaceService.deleteWorkspace(id, userReq);
    logger.info("Deleted workspace {} for {}", id, userReq.getEmail());

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> createDataReference(
      @PathVariable("id") UUID id, @RequestBody CreateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        "Creating data reference in workspace {} for {} with body {}",
        id,
        userReq.getEmail(),
        body);

    ControllerValidationUtils.validate(body);
    DataReferenceValidationUtils.validateReferenceName(body.getName());
    // TODO: this will require more translation when we add additional reference types.
    DataReferenceType referenceType = DataReferenceType.fromApiModel(body.getReferenceType());
    SnapshotReference snapshot =
        SnapshotReference.create(
            body.getReference().getInstanceName(), body.getReference().getSnapshot());
    dataReferenceValidation.validateReferenceObject(snapshot, referenceType, userReq);

    DataReferenceRequest referenceRequest =
        DataReferenceRequest.builder()
            .workspaceId(id)
            .name(body.getName())
            .description(body.getDescription())
            .referenceType(referenceType)
            .cloningInstructions(CloningInstructions.fromApiModel(body.getCloningInstructions()))
            .referenceObject(snapshot)
            .build();
    DataReference reference = dataReferenceService.createDataReference(referenceRequest, userReq);
    logger.info(
        "Created data reference {} in workspace {} for {} ", reference, id, userReq.getEmail());

    return new ResponseEntity<>(reference.toApiModel(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReference(
      @PathVariable("id") UUID workspaceId, @PathVariable("referenceId") UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        "Getting data reference by id {} in workspace {} for {}",
        referenceId,
        workspaceId,
        userReq.getEmail());
    DataReference ref = dataReferenceService.getDataReference(workspaceId, referenceId, userReq);
    logger.info(
        "Got data reference {} in workspace {} for {}", ref, workspaceId, userReq.getEmail());
    // TODO(PF-404): this endpoint's return type does not support reference types beyond snapshots.
    // Clients should migrate to type-specific endpoints, and this endpoint should be removed.
    if (ref.referenceType() != DataReferenceType.DATA_REPO_SNAPSHOT) {
      throw new InvalidDataReferenceException(
          "This endpoint does not support non-snapshot references. Use the newer type-specific endpoints instead.");
    }
    return new ResponseEntity<>(ref.toApiModel(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReferenceByName(
      @PathVariable("id") UUID workspaceId,
      @PathVariable("referenceType") ReferenceTypeEnum referenceType,
      @PathVariable("name") String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    // TODO(PF-404): this endpoint's return type does not support reference types beyond snapshots.
    // Clients should migrate to type-specific endpoints, and this endpoint should be removed.
    if (referenceType != ReferenceTypeEnum.DATA_REPO_SNAPSHOT) {
      throw new InvalidDataReferenceException(
          "This endpoint does not support non-snapshot references. Use the newer type-specific endpoints instead.");
    }
    logger.info(
        "Getting data reference by name {} and reference type {} in workspace {} for {}",
        name,
        referenceType,
        workspaceId,
        userReq.getEmail());
    DataReferenceValidationUtils.validateReferenceName(name);
    DataReference ref =
        dataReferenceService.getDataReferenceByName(
            workspaceId, DataReferenceType.fromApiModel(referenceType), name, userReq);
    logger.info(
        "Got data reference {} of type {} in workspace {} for {}",
        ref,
        referenceType,
        workspaceId,
        userReq.getEmail());

    return new ResponseEntity<>(ref.toApiModel(), HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataReference(
      @PathVariable("id") UUID id,
      @PathVariable("referenceId") UUID referenceId,
      @RequestBody UpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();

    if (body.getName() == null && body.getDescription() == null) {
      throw new InvalidDataReferenceException("Must specify name or description to update.");
    }

    if (body.getName() != null) {
      DataReferenceValidationUtils.validateReferenceName(body.getName());
    }

    logger.info(
        "Updating data reference by id {} in workspace {} for {} with body {}",
        referenceId,
        id,
        userReq.getEmail(),
        body);

    dataReferenceService.updateDataReference(id, referenceId, body, userReq);
    logger.info(
        "Updating data reference by id {} in workspace {} for {} with body {}",
        referenceId,
        id,
        userReq.getEmail(),
        body);

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteDataReference(
      @PathVariable("id") UUID workspaceId, @PathVariable("referenceId") UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        "Deleting data reference by id {} in workspace {} for {}",
        referenceId,
        workspaceId,
        userReq.getEmail());
    dataReferenceService.deleteDataReference(workspaceId, referenceId, userReq);
    logger.info(
        "Deleted data reference by id {} in workspace {} for {}",
        referenceId,
        workspaceId,
        userReq.getEmail());

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<DataReferenceList> enumerateReferences(
      @PathVariable("id") UUID id,
      @Valid @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
      @Valid @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info("Getting snapshot data references in workspace {} for {}", id, userReq.getEmail());
    ControllerValidationUtils.validatePaginationParams(offset, limit);
    List<DataReference> enumerateResult =
        dataReferenceService.enumerateDataReferences(id, offset, limit, userReq);
    logger.info("Got snapshot data references in workspace {} for {}", id, userReq.getEmail());
    DataReferenceList responseList = new DataReferenceList();
    for (DataReference ref : enumerateResult) {
      // TODO(PF-404): this is a workaround until clients migrate off this endpoint.
      if (ref.referenceType() == DataReferenceType.DATA_REPO_SNAPSHOT) {
        responseList.addResourcesItem(ref.toApiModel());
      }
    }
    return ResponseEntity.ok(responseList);
  }

  @Override
  public ResponseEntity<Void> grantRole(
      @PathVariable("id") UUID id,
      @PathVariable("role") IamRole role,
      @RequestBody GrantRoleRequestBody body) {
    ControllerValidationUtils.validateEmail(body.getMemberEmail());
    samService.grantWorkspaceRole(
        id,
        getAuthenticatedInfo(),
        bio.terra.workspace.service.iam.model.IamRole.fromApiModel(role),
        body.getMemberEmail());
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> removeRole(
      @PathVariable("id") UUID id,
      @PathVariable("role") IamRole role,
      @PathVariable("memberEmail") String memberEmail) {
    ControllerValidationUtils.validateEmail(memberEmail);
    samService.removeWorkspaceRole(
        id,
        getAuthenticatedInfo(),
        bio.terra.workspace.service.iam.model.IamRole.fromApiModel(role),
        memberEmail);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<RoleBindingList> getRoles(@PathVariable("id") UUID id) {
    List<bio.terra.workspace.service.iam.model.RoleBinding> bindingList =
        samService.listRoleBindings(id, getAuthenticatedInfo());
    RoleBindingList responseList = new RoleBindingList();
    for (bio.terra.workspace.service.iam.model.RoleBinding roleBinding : bindingList) {
      responseList.add(
          new bio.terra.workspace.generated.model.RoleBinding()
              .role(roleBinding.role().toApiModel())
              .members(roleBinding.users()));
    }
    return new ResponseEntity<>(responseList, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<CreateCloudContextResult> createCloudContext(
      UUID id, @Valid CreateCloudContextRequest body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ControllerValidationUtils.validateCloudType(body.getCloudType());
    String jobId = body.getJobControl().getId();
    String resultPath = getAsyncResultEndpoint(jobId);

    // For now, the cloud type is always GCP and that is guaranteed in the validate.
    workspaceService.createGcpCloudContext(id, jobId, resultPath, userReq);
    CreateCloudContextResult response = fetchCreateCloudContextResult(jobId, userReq);
    return new ResponseEntity<>(
        response, HttpStatus.valueOf(response.getJobReport().getStatusCode()));
  }

  @Override
  public ResponseEntity<CreateCloudContextResult> getCreateCloudContextResult(
      UUID id, String jobId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    CreateCloudContextResult response = fetchCreateCloudContextResult(jobId, userReq);
    return new ResponseEntity<>(
        response, HttpStatus.valueOf(response.getJobReport().getStatusCode()));
  }

  private CreateCloudContextResult fetchCreateCloudContextResult(
      String jobId, AuthenticatedUserRequest userReq) {
    final AsyncJobResult<GcpCloudContext> jobResult =
        jobService.retrieveAsyncJobResult(jobId, GcpCloudContext.class, userReq);

    final GcpContext gcpContext;
    if (jobResult.getJobReport().getStatus().equals(StatusEnum.SUCCEEDED)) {
      gcpContext = new GcpContext().projectId(jobResult.getResult().getGcpProjectId());
    } else {
      gcpContext = null;
    }
    return new CreateCloudContextResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getErrorReport())
        .gcpContext(gcpContext);
  }

  @Override
  public ResponseEntity<Void> deleteCloudContext(UUID id, CloudContext cloudContext) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    ControllerValidationUtils.validateCloudType(cloudContext);
    workspaceService.deleteGcpCloudContext(id, userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
