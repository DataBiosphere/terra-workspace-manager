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
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.GcpContext;
import bio.terra.workspace.generated.model.GrantRoleRequestBody;
import bio.terra.workspace.generated.model.IamRole;
import bio.terra.workspace.generated.model.JobReport.StatusEnum;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.generated.model.RoleBindingList;
import bio.terra.workspace.generated.model.UpdateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.generated.model.WorkspaceStageModel;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.exception.InvalidRoleException;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.reference.ReferenceDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.reference.ReferenceResource;
import bio.terra.workspace.service.resource.reference.ReferenceResourceService;
import bio.terra.workspace.service.resource.reference.exception.InvalidReferenceException;
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
  private final JobService jobService;
  private final SamService samService;
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;
  private final ReferenceResourceService referenceResourceService;

  @Autowired
  public WorkspaceApiController(
      WorkspaceService workspaceService,
      JobService jobService,
      SamService samService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      ReferenceResourceService referenceResourceService) {
    this.workspaceService = workspaceService;
    this.jobService = jobService;
    this.samService = samService;
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
    this.referenceResourceService = referenceResourceService;
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
  public ResponseEntity<WorkspaceDescription> getWorkspace(@PathVariable("workspaceId") UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info("Getting workspace {} for {}", id, userReq.getEmail());
    Workspace workspace = workspaceService.getWorkspace(id, userReq);
    GcpContext gcpContext = workspace.getGcpCloudContext().map(GcpCloudContext::toApi).orElse(null);

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
  public ResponseEntity<Void> deleteWorkspace(@PathVariable("workspaceId") UUID id) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info("Deleting workspace {} for {}", id, userReq.getEmail());
    workspaceService.deleteWorkspace(id, userReq);
    logger.info("Deleted workspace {} for {}", id, userReq.getEmail());

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  // TODO(PF-404): the following DataReference endpoints are deprecated and will go away

  @Override
  public ResponseEntity<DataReferenceDescription> createDataReference(
      @PathVariable("workspaceId") UUID id, @RequestBody CreateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        "Creating data reference in workspace {} for {} with body {}",
        id,
        userReq.getEmail(),
        body);

    ControllerValidationUtils.validate(body);
    ValidationUtils.validateResourceName(body.getName());

    var resource =
        new ReferenceDataRepoSnapshotResource(
            id,
            UUID.randomUUID(), // mint a resource id for this bucket
            body.getName(),
            body.getDescription(),
            CloningInstructions.fromApiModel(body.getCloningInstructions()),
            body.getReference().getInstanceName(),
            body.getReference().getSnapshot());

    ReferenceResource referenceResource =
        referenceResourceService.createReferenceResource(resource, getAuthenticatedInfo());
    DataReferenceDescription response = makeDataReferenceDescription(referenceResource);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReference(
      @PathVariable("workspaceId") UUID workspaceId,
      @PathVariable("referenceId") UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        "Getting data reference by id {} in workspace {} for {}",
        referenceId,
        workspaceId,
        userReq.getEmail());

    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResource(workspaceId, referenceId, userReq);

    // TODO(PF-404): this endpoint's return type does not support reference types beyond snapshots.
    // Clients should migrate to type-specific endpoints, and this endpoint should be removed.
    if (referenceResource.getResourceType() != WsmResourceType.DATA_REPO_SNAPSHOT) {
      throw new InvalidReferenceException(
          "This endpoint does not support non-snapshot references. Use the newer type-specific endpoints instead.");
    }

    DataReferenceDescription response = makeDataReferenceDescription(referenceResource);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<DataReferenceDescription> getDataReferenceByName(
      @PathVariable("workspaceId") UUID workspaceId,
      @PathVariable("referenceType") ReferenceTypeEnum referenceType,
      @PathVariable("name") String name) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    // TODO(PF-404): this endpoint's return type does not support reference types beyond snapshots.
    // Clients should migrate to type-specific endpoints, and this endpoint should be removed.
    if (referenceType != ReferenceTypeEnum.DATA_REPO_SNAPSHOT) {
      throw new InvalidReferenceException(
          "This endpoint does not support non-snapshot references. Use the newer type-specific endpoints instead.");
    }
    ValidationUtils.validateResourceName(name);

    ReferenceResource referenceResource =
        referenceResourceService.getReferenceResourceByName(workspaceId, name, userReq);
    DataReferenceDescription response = makeDataReferenceDescription(referenceResource);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> updateDataReference(
      @PathVariable("workspaceId") UUID id,
      @PathVariable("referenceId") UUID referenceId,
      @RequestBody UpdateDataReferenceRequestBody body) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();

    if (body.getName() == null && body.getDescription() == null) {
      throw new InvalidReferenceException("Must specify name or description to update.");
    }

    if (body.getName() != null) {
      ValidationUtils.validateResourceName(body.getName());
    }

    referenceResourceService.updateReferenceResource(
        id, referenceId, body.getName(), body.getDescription(), userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> deleteDataReference(
      @PathVariable("workspaceId") UUID workspaceId,
      @PathVariable("referenceId") UUID referenceId) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info(
        "Deleting data reference by id {} in workspace {} for {}",
        referenceId,
        workspaceId,
        userReq.getEmail());

    referenceResourceService.deleteReferenceResource(workspaceId, referenceId, userReq);

    logger.info(
        "Deleted data reference by id {} in workspace {} for {}",
        referenceId,
        workspaceId,
        userReq.getEmail());

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<DataReferenceList> enumerateReferences(
      @PathVariable("workspaceId") UUID id,
      @Valid @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
      @Valid @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
    logger.info("Getting snapshot data references in workspace {} for {}", id, userReq.getEmail());
    ControllerValidationUtils.validatePaginationParams(offset, limit);
    List<ReferenceResource> enumerateResult =
        referenceResourceService.enumerateReferences(id, offset, limit, userReq);

    // TODO(PF-404): this is a workaround until clients migrate off this endpoint.
    DataReferenceList responseList = new DataReferenceList();
    for (ReferenceResource resource : enumerateResult) {
      if (resource.getResourceType() == WsmResourceType.DATA_REPO_SNAPSHOT) {
        responseList.addResourcesItem(makeDataReferenceDescription(resource));
      }
    }
    return ResponseEntity.ok(responseList);
  }

  private DataReferenceDescription makeDataReferenceDescription(
      ReferenceResource referenceResource) {
    ReferenceDataRepoSnapshotResource snapshotResource =
        referenceResource.castToDataRepoSnapshotResource();
    var reference =
        new DataRepoSnapshot()
            .instanceName(snapshotResource.getInstanceName())
            .snapshot(snapshotResource.getSnapshotId());
    return new DataReferenceDescription()
        .referenceId(referenceResource.getResourceId())
        .name(referenceResource.getName())
        .description(referenceResource.getDescription())
        .workspaceId(referenceResource.getWorkspaceId())
        .cloningInstructions(referenceResource.getCloningInstructions().toApiModel())
        .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
        .reference(reference);
  }

  @Override
  public ResponseEntity<Void> grantRole(
      @PathVariable("workspaceId") UUID id,
      @PathVariable("role") IamRole role,
      @RequestBody GrantRoleRequestBody body) {
    ControllerValidationUtils.validateEmail(body.getMemberEmail());
    if (role == IamRole.APPLICATION) {
      throw new InvalidRoleException(
          "Users cannot grant role APPLICATION. Use application registration instead.");
    }
    samService.grantWorkspaceRole(
        id,
        getAuthenticatedInfo(),
        bio.terra.workspace.service.iam.model.IamRole.fromApiModel(role),
        body.getMemberEmail());
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<Void> removeRole(
      @PathVariable("workspaceId") UUID id,
      @PathVariable("role") IamRole role,
      @PathVariable("memberEmail") String memberEmail) {
    ControllerValidationUtils.validateEmail(memberEmail);
    if (role == IamRole.APPLICATION) {
      throw new InvalidRoleException(
          "Users cannot remove role APPLICATION. Use application registration instead.");
    }
    samService.removeWorkspaceRole(
        id,
        getAuthenticatedInfo(),
        bio.terra.workspace.service.iam.model.IamRole.fromApiModel(role),
        memberEmail);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Override
  public ResponseEntity<RoleBindingList> getRoles(@PathVariable("workspaceId") UUID id) {
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
    ControllerValidationUtils.validateCloudPlatform(body.getCloudType());
    AuthenticatedUserRequest userReq = getAuthenticatedInfo();
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
    ControllerValidationUtils.validateCloudPlatform(cloudContext);
    workspaceService.deleteGcpCloudContext(id, userReq);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
