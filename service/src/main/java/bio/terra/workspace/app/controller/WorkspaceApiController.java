package bio.terra.workspace.app.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertApiPropertyToMap;
import static bio.terra.workspace.common.utils.ControllerValidationUtils.validatePropertiesDeleteRequestBody;
import static bio.terra.workspace.common.utils.ControllerValidationUtils.validatePropertiesUpdateRequestBody;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoDescription;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.app.controller.shared.WorkspaceApiUtils;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceRequest;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateCloudContextRequest;
import bio.terra.workspace.generated.model.ApiCreateCloudContextResult;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiDeleteCloudContextV2Request;
import bio.terra.workspace.generated.model.ApiDeleteWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiGcpContext;
import bio.terra.workspace.generated.model.ApiGrantRoleRequestBody;
import bio.terra.workspace.generated.model.ApiIamRole;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.generated.model.ApiMergeCheckRequest;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiRegions;
import bio.terra.workspace.generated.model.ApiRoleBinding;
import bio.terra.workspace.generated.model.ApiRoleBindingList;
import bio.terra.workspace.generated.model.ApiUpdateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceDescriptionList;
import bio.terra.workspace.generated.model.ApiWsmPolicyExplainResult;
import bio.terra.workspace.generated.model.ApiWsmPolicyMergeCheckResult;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateRequest;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.exception.InvalidRoleException;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.policy.TpsApiConversionUtils;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.TpsUtilities;
import bio.terra.workspace.service.policy.model.PolicyExplainResult;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceDescription;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

@Controller
public class WorkspaceApiController extends ControllerBase implements WorkspaceApi {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceApiController.class);
  private final WorkspaceService workspaceService;
  private final WorkspaceActivityLogService workspaceActivityLogService;
  private final PetSaService petSaService;
  private final TpsApiDispatch tpsApiDispatch;
  private final ResourceDao resourceDao;
  private final SpendProfileService spendProfileService;
  private final WorkspaceV2Api workspaceV2Api;
  private final WorkspaceApiUtils workspaceApiUtils;

  @Autowired
  public WorkspaceApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      FeatureConfiguration features,
      FeatureService featureService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      WorkspaceService workspaceService,
      WorkspaceActivityLogService workspaceActivityLogService,
      PetSaService petSaService,
      TpsApiDispatch tpsApiDispatch,
      ResourceDao resourceDao,
      SpendProfileService spendProfileService,
      WorkspaceV2Api workspaceV2Api,
      WorkspaceApiUtils workspaceApiUtils) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        features,
        featureService,
        jobService,
        jobApiUtils);
    this.workspaceService = workspaceService;
    this.petSaService = petSaService;
    this.tpsApiDispatch = tpsApiDispatch;
    this.workspaceActivityLogService = workspaceActivityLogService;
    this.resourceDao = resourceDao;
    this.spendProfileService = spendProfileService;
    this.workspaceV2Api = workspaceV2Api;
    this.workspaceApiUtils = workspaceApiUtils;
  }

  // For the WorkspaceV2 interfaces, dispatch to a separate module for the implementation
  @Traced
  @Override
  public ResponseEntity<ApiCreateWorkspaceV2Result> createWorkspaceV2(
      ApiCreateWorkspaceV2Request body) {
    return workspaceV2Api.createWorkspaceV2(body);
  }

  @Traced
  @Override
  public ResponseEntity<ApiJobResult> deleteCloudContextV2(
      UUID workspaceId, ApiCloudPlatform cloudContext, ApiDeleteCloudContextV2Request body) {
    return workspaceV2Api.deleteCloudContextV2(workspaceId, cloudContext, body);
  }

  @Traced
  @Override
  public ResponseEntity<ApiJobResult> deleteWorkspaceV2(
      UUID workspaceId, ApiDeleteWorkspaceV2Request body) {
    return workspaceV2Api.deleteWorkspaceV2(workspaceId, body);
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreateWorkspaceV2Result> getCreateWorkspaceV2Result(String jobId) {
    return workspaceV2Api.getCreateWorkspaceV2Result(jobId);
  }

  @Traced
  @Override
  public ResponseEntity<ApiJobResult> getDeleteCloudContextV2Result(
      UUID workspaceId, String jobId) {
    return workspaceV2Api.getDeleteCloudContextV2Result(workspaceId, jobId);
  }

  @Traced
  @Override
  public ResponseEntity<ApiJobResult> getDeleteWorkspaceV2Result(UUID workspaceId, String jobId) {
    return workspaceV2Api.getDeleteWorkspaceV2Result(workspaceId, jobId);
  }

  @Traced
  @Deprecated
  @Override
  public ResponseEntity<ApiCreatedWorkspace> createWorkspace(
      @RequestBody ApiCreateWorkspaceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info(
        "Creating workspace {} for {} subject {}",
        body.getId(),
        userRequest.getEmail(),
        userRequest.getSubjectId());

    SpendProfile spendProfile =
        workspaceApiUtils.validateSpendProfilePermission(userRequest, body.getSpendProfile());
    SpendProfileId spendProfileId = (spendProfile == null) ? null : spendProfile.id();

    TpsPolicyInputs policies =
        workspaceApiUtils.validateAndConvertPolicies(body.getPolicies(), body.getStage());
    WorkspaceStage workspaceStage = WorkspaceApiUtils.getStageFromApiStage(body.getStage());

    // WSM requires a userFacingId. Create one, if it is not provided.
    String userFacingId =
        Optional.ofNullable(body.getUserFacingId()).orElse(body.getId().toString());
    ControllerValidationUtils.validateUserFacingId(userFacingId);

    Workspace workspace =
        Workspace.builder()
            .workspaceId(body.getId())
            .userFacingId(userFacingId)
            .displayName(body.getDisplayName())
            .description(body.getDescription())
            .spendProfileId(spendProfileId)
            .workspaceStage(workspaceStage)
            .properties(convertApiPropertyToMap(body.getProperties()))
            .createdByEmail(samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest))
            .build();
    UUID createdWorkspaceUuid =
        workspaceService.createWorkspace(
            workspace, policies, body.getApplicationIds(), userRequest);

    ApiCreatedWorkspace responseWorkspace = new ApiCreatedWorkspace().id(createdWorkspaceUuid);
    logger.info("Created workspace {} for {}", responseWorkspace, userRequest.getEmail());

    return new ResponseEntity<>(responseWorkspace, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiWorkspaceDescriptionList> listWorkspaces(
      Integer offset, Integer limit, ApiIamRole minimumHighestRole) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info("Listing workspaces for {}", userRequest.getEmail());
    ControllerValidationUtils.validatePaginationParams(offset, limit);
    // Can't set default in yaml (https://stackoverflow.com/a/68542868/6447189), so set here.
    if (minimumHighestRole == null) {
      minimumHighestRole = ApiIamRole.READER;
    }

    // Unlike most other operations, there's no Sam permission required to list workspaces. As long
    // as
    // a user is enabled, they can call this endpoint, though they may not have any workspaces they
    // can read.
    List<WorkspaceDescription> workspaceDescriptions =
        workspaceService.getWorkspaceDescriptions(
            userRequest, offset, limit, WsmIamRole.fromApiModel(minimumHighestRole));

    var response =
        new ApiWorkspaceDescriptionList()
            .workspaces(
                workspaceDescriptions.stream()
                    .map(workspaceApiUtils::buildApiWorkspaceDescription)
                    .toList());
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiWorkspaceDescription> getWorkspace(
      @PathVariable("workspaceId") UUID uuid, ApiIamRole minimumHighestRole) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info("Getting workspace {} for {}", uuid, userRequest.getEmail());
    // Can't set default in yaml (https://stackoverflow.com/a/68542868/6447189), so set here.
    if (minimumHighestRole == null) {
      minimumHighestRole = ApiIamRole.READER;
    }
    String samAction = WsmIamRole.fromApiModel(minimumHighestRole).toSamAction();
    Workspace workspace = workspaceService.validateWorkspaceAndAction(userRequest, uuid, samAction);

    WsmIamRole highestRole = workspaceService.getHighestRole(uuid, userRequest);
    ApiWorkspaceDescription desc =
        workspaceApiUtils.buildWorkspaceDescription(workspace, highestRole);
    logger.info("Got workspace {} for {}", desc, userRequest.getEmail());

    return new ResponseEntity<>(desc, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiWorkspaceDescription> getWorkspaceByUserFacingId(
      @PathVariable("workspaceUserFacingId") String userFacingId, ApiIamRole minimumHighestRole) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info("Getting workspace {} for {}", userFacingId, userRequest.getEmail());
    // Can't set default in yaml (https://stackoverflow.com/a/68542868/6447189), so set here.
    if (minimumHighestRole == null) {
      minimumHighestRole = ApiIamRole.READER;
    }
    // Authz check is inside workspaceService here as we would need the UUID to check Sam, but
    // we only have the UFID at this point.

    Workspace workspace =
        workspaceService.getWorkspaceByUserFacingId(
            userFacingId, userRequest, WsmIamRole.fromApiModel(minimumHighestRole));
    WsmIamRole highestRole =
        workspaceService.getHighestRole(workspace.getWorkspaceId(), userRequest);
    ApiWorkspaceDescription desc =
        workspaceApiUtils.buildWorkspaceDescription(workspace, highestRole);
    logger.info("Got workspace {} for {}", desc, userRequest.getEmail());

    return new ResponseEntity<>(desc, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiWorkspaceDescription> updateWorkspace(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @RequestBody ApiUpdateWorkspaceRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info("Updating workspace {} for {}", workspaceUuid, userRequest.getEmail());

    if (body.getUserFacingId() != null) {
      ControllerValidationUtils.validateUserFacingId(body.getUserFacingId());
    }
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);

    workspace =
        workspaceService.updateWorkspace(
            workspaceUuid,
            body.getUserFacingId(),
            body.getDisplayName(),
            body.getDescription(),
            userRequest);
    WsmIamRole highestRole = workspaceService.getHighestRole(workspaceUuid, userRequest);
    ApiWorkspaceDescription desc =
        workspaceApiUtils.buildWorkspaceDescription(workspace, highestRole);
    logger.info("Updated workspace {} for {}", desc, userRequest.getEmail());

    return new ResponseEntity<>(desc, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiWsmPolicyUpdateResult> updatePolicies(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @RequestBody ApiWsmPolicyUpdateRequest body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, SamWorkspaceAction.OWN);
    workspaceService.validateWorkspaceState(workspace);

    features.tpsEnabledCheck();
    TpsPolicyInputs adds = TpsApiConversionUtils.tpsFromApiTpsPolicyInputs(body.getAddAttributes());
    TpsPolicyInputs removes =
        TpsApiConversionUtils.tpsFromApiTpsPolicyInputs(body.getRemoveAttributes());
    TpsUpdateMode updateMode = TpsApiConversionUtils.tpsFromApiTpsUpdateMode(body.getUpdateMode());

    var result =
        workspaceService.updatePolicy(workspaceUuid, adds, removes, updateMode, userRequest);

    ApiWsmPolicyUpdateResult apiResult = TpsApiConversionUtils.apiFromTpsUpdateResult(result);
    return new ResponseEntity<>(apiResult, HttpStatus.OK);
  }

  @Traced
  @Deprecated
  @Override
  public ResponseEntity<Void> deleteWorkspace(@PathVariable("workspaceId") UUID uuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info("Deleting workspace {} for {}", uuid, userRequest.getEmail());
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.DELETE);
    workspaceService.validateWorkspaceState(workspace);
    workspaceService.deleteWorkspace(workspace, userRequest);
    logger.info("Deleted workspace {} for {}", uuid, userRequest.getEmail());

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Traced
  @Override
  public ResponseEntity<Void> deleteWorkspaceProperties(
      @PathVariable("workspaceId") UUID workspaceUuid, @RequestBody List<String> propertyKeys) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamWorkspaceAction.DELETE);
    workspaceService.validateWorkspaceState(workspace);
    validatePropertiesDeleteRequestBody(propertyKeys);
    logger.info("Deleting the properties in workspace {}", workspaceUuid);
    workspaceService.deleteWorkspaceProperties(workspaceUuid, propertyKeys, userRequest);
    logger.info("Deleted the properties in workspace {}", workspaceUuid);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Traced
  @Override
  public ResponseEntity<Void> updateWorkspaceProperties(
      @PathVariable("workspaceId") UUID workspaceUuid, @RequestBody List<ApiProperty> properties) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateWorkspaceAndAction(
            userRequest, workspaceUuid, SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);
    validatePropertiesUpdateRequestBody(properties);
    Map<String, String> propertyMap = convertApiPropertyToMap(properties);
    logger.info("Updating the properties {} in workspace {}", propertyMap, workspaceUuid);
    workspaceService.updateWorkspaceProperties(workspaceUuid, propertyMap, userRequest);
    logger.info("Updated the properties {} in workspace {}", propertyMap, workspaceUuid);

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Traced
  @Override
  public ResponseEntity<Void> grantRole(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @PathVariable("role") ApiIamRole role,
      @RequestBody ApiGrantRoleRequestBody body) {
    ControllerValidationUtils.validateEmail(body.getMemberEmail());
    if (role == ApiIamRole.APPLICATION) {
      throw new InvalidRoleException(
          "Users cannot grant role APPLICATION. Use application registration instead.");
    }
    workspaceService.validateWorkspaceState(workspaceUuid);
    // No additional authz check as this is just a wrapper around a Sam endpoint.
    Rethrow.onInterrupted(
        () ->
            samService.grantWorkspaceRole(
                workspaceUuid,
                getAuthenticatedInfo(),
                WsmIamRole.fromApiModel(role),
                body.getMemberEmail()),
        "grantWorkspaceRole");
    workspaceActivityLogService.writeActivity(
        getAuthenticatedInfo(),
        workspaceUuid,
        OperationType.GRANT_WORKSPACE_ROLE,
        body.getMemberEmail(),
        ActivityLogChangedTarget.USER);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Traced
  @Override
  public ResponseEntity<Void> removeRole(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @PathVariable("role") ApiIamRole role,
      @PathVariable("memberEmail") String memberEmail) {
    ControllerValidationUtils.validateEmail(memberEmail);
    if (role == ApiIamRole.APPLICATION) {
      throw new InvalidRoleException(
          "Users cannot remove role APPLICATION. Use application registration instead.");
    }
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, SamWorkspaceAction.OWN);
    workspaceService.validateWorkspaceState(workspace);
    workspaceService.removeWorkspaceRoleFromUser(
        workspace, WsmIamRole.fromApiModel(role), memberEmail, userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Traced
  @Override
  public ResponseEntity<ApiRoleBindingList> getRoles(@PathVariable("workspaceId") UUID uuid) {
    // No additional authz check as this is just a wrapper around a Sam endpoint.
    List<bio.terra.workspace.service.iam.model.RoleBinding> bindingList =
        Rethrow.onInterrupted(
            () -> samService.listRoleBindings(uuid, getAuthenticatedInfo()), "listRoleBindings");
    ApiRoleBindingList responseList = new ApiRoleBindingList();
    for (bio.terra.workspace.service.iam.model.RoleBinding roleBinding : bindingList) {
      responseList.add(
          new ApiRoleBinding().role(roleBinding.role().toApiModel()).members(roleBinding.users()));
    }
    return new ResponseEntity<>(responseList, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreateCloudContextResult> createCloudContext(
      UUID uuid, @Valid ApiCreateCloudContextRequest body) {
    ApiCloudPlatform cloudPlatform = body.getCloudPlatform();
    ControllerValidationUtils.validateCloudPlatform(cloudPlatform);
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String jobId = body.getJobControl().getId();
    String resultPath = getAsyncResultEndpoint(jobId);

    // Authorize creation of context in the workspace
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);

    // TODO: PF-2694 REST API part
    //  When we make the REST API changes, the spend profile will come with the create cloud context
    //  and we will take it from there. Only if missing, will we take it from the workspace. For
    //  this part, we do the permission check using the workspace spend profile.
    SpendProfileId spendProfileId =
        workspace
            .getSpendProfileId()
            .orElseThrow(() -> MissingSpendProfileException.forWorkspace(workspace.workspaceId()));

    // Make sure the caller is authorized to use the spend profile
    SpendProfile spendProfile =
        spendProfileService.authorizeLinking(
            spendProfileId, features.isBpmGcpEnabled(), userRequest);

    workspaceService.createCloudContext(
        workspace,
        CloudPlatform.fromApiCloudPlatform(cloudPlatform),
        spendProfile,
        jobId,
        userRequest,
        resultPath);

    ApiCreateCloudContextResult response = fetchCreateCloudContextResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Traced
  @Override
  public ResponseEntity<ApiCreateCloudContextResult> getCreateCloudContextResult(
      UUID uuid, String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, uuid);
    ApiCreateCloudContextResult response = fetchCreateCloudContextResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  private ApiCreateCloudContextResult fetchCreateCloudContextResult(String jobId) {
    JobApiUtils.AsyncJobResult<CloudContext> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, CloudContext.class);

    ApiGcpContext gcpApiContext = null;
    ApiAzureContext azureApiContext = null;
    ApiAwsContext awsApiContext = null;

    if (jobResult.getJobReport().getStatus().equals(StatusEnum.SUCCEEDED)) {
      CloudContext cloudContext = jobResult.getResult();
      if (cloudContext == null) {
        // This is a bad state. We should never see the job succeed without the cloud
        // context populated.
        throw new InternalLogicException("Expected cloud context result, but found null");
      }

      switch (cloudContext.getCloudPlatform()) {
        case AWS -> {
          AwsCloudContext awsCloudContext = cloudContext.castByEnum(CloudPlatform.AWS);
          awsApiContext = awsCloudContext.toApi();
        }
        case AZURE -> {
          AzureCloudContext azureCloudContext = cloudContext.castByEnum(CloudPlatform.AZURE);
          azureApiContext = azureCloudContext.toApi();
        }
        case GCP -> {
          GcpCloudContext gcpCloudContext = cloudContext.castByEnum(CloudPlatform.GCP);
          gcpApiContext = gcpCloudContext.toApi();
        }
        default -> throw new InternalLogicException("Invalid cloud platform returned");
      }
    }

    return new ApiCreateCloudContextResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .gcpContext(gcpApiContext)
        .azureContext(azureApiContext)
        .awsContext(awsApiContext);
  }

  @Traced
  @Deprecated
  @Override
  public ResponseEntity<Void> deleteCloudContext(UUID uuid, ApiCloudPlatform cloudPlatform) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControllerValidationUtils.validateCloudPlatform(cloudPlatform);
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(userRequest, uuid, SamWorkspaceAction.WRITE);
    workspaceService.validateWorkspaceState(workspace);
    workspaceService.deleteCloudContext(
        workspace, CloudPlatform.fromApiCloudPlatform(cloudPlatform), userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Traced
  @Override
  public ResponseEntity<Void> enablePet(UUID workspaceUuid) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // Validate that the user is a workspace member, as enablePetServiceAccountImpersonation does
    // not authenticate.
    workspaceService.validateMcWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    petSaService.enablePetServiceAccountImpersonation(
        workspaceUuid,
        samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
        userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  /**
   * Clone an entire workspace by creating a new workspace and cloning the workspace's resources
   * into it.
   *
   * @param workspaceUuid - ID of source workspace
   * @param body - request body
   * @return - result structure for the overall clone operation with details for each resource
   */
  @Traced
  @Override
  public ResponseEntity<ApiCloneWorkspaceResult> cloneWorkspace(
      UUID workspaceUuid, @Valid ApiCloneWorkspaceRequest body) {
    final AuthenticatedUserRequest petRequest = getCloningCredentials(workspaceUuid);

    // Clone is creating the destination workspace so unlike other clone operations there's no
    // additional authz check for the destination. As long as the user is enabled in Sam, they can
    // create a new workspace.
    final Workspace sourceWorkspace =
        workspaceService.validateWorkspaceAndAction(
            petRequest, workspaceUuid, SamWorkspaceAction.READ);
    workspaceService.validateWorkspaceState(sourceWorkspace);

    // TODO: PF-2694 REST API part
    //  When we make the REST API changes, the spend profile will come with the source cloud context
    //  and we will take it from there. Only if missing, will we take it from the workspace. For
    //  this part, we do the retrieval and permission check using the workspace spend profile.
    Optional<SpendProfileId> spendProfileIdOptional =
        Optional.ofNullable(body.getSpendProfile()).map(SpendProfileId::new);
    SpendProfileId spendProfileId =
        spendProfileIdOptional.orElse(
            sourceWorkspace
                .getSpendProfileId()
                .orElseThrow(
                    () ->
                        MissingSpendProfileException.forWorkspace(sourceWorkspace.workspaceId())));

    SpendProfile spendProfile =
        spendProfileService.authorizeLinking(
            spendProfileId, features.isBpmGcpEnabled(), petRequest);

    // Accept a target workspace id if one is provided. This allows Rawls to specify an
    // existing workspace id. WSM then creates the WSMspace supporting the Rawls workspace.
    UUID destinationWorkspaceId =
        Optional.ofNullable(body.getDestinationWorkspaceId()).orElse(UUID.randomUUID());

    // ET uses userFacingId; CWB doesn't. Schema enforces that userFacingId must be set. CWB doesn't
    // pass userFacingId in request, so use id.
    String destinationUserFacingId =
        Optional.ofNullable(body.getUserFacingId()).orElse(destinationWorkspaceId.toString());
    ControllerValidationUtils.validateUserFacingId(destinationUserFacingId);

    // If user does not specify the destinationWorkspace's displayName, then we will generate the
    // name followed the sourceWorkspace's displayName, if sourceWorkspace's displayName is null, we
    // will generate the name based on the sourceWorkspace's userFacingId.
    String generatedDisplayName =
        sourceWorkspace.getDisplayName().orElse(sourceWorkspace.getUserFacingId()) + " (Copy)";

    // Construct the target workspace object from the inputs
    // Policies are cloned in the flight instead of here so that they get cleaned appropriately if
    // the flight fails.
    final Workspace destinationWorkspace =
        Workspace.builder()
            .workspaceId(destinationWorkspaceId)
            .userFacingId(destinationUserFacingId)
            .spendProfileId(spendProfileId)
            .workspaceStage(sourceWorkspace.getWorkspaceStage())
            .displayName(Optional.ofNullable(body.getDisplayName()).orElse(generatedDisplayName))
            .description(body.getDescription())
            .properties(sourceWorkspace.getProperties())
            .createdByEmail(samService.getUserEmailFromSamAndRethrowOnInterrupt(petRequest))
            .build();

    final String jobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace,
            petRequest,
            body.getLocation(),
            TpsApiConversionUtils.tpsFromApiTpsPolicyInputs(body.getAdditionalPolicies()),
            destinationWorkspace,
            spendProfile);

    final ApiCloneWorkspaceResult result = fetchCloneWorkspaceResult(jobId);
    final ApiClonedWorkspace clonedWorkspaceStub =
        new ApiClonedWorkspace()
            .destinationWorkspaceId(destinationWorkspaceId)
            .destinationUserFacingId(destinationUserFacingId)
            .sourceWorkspaceId(workspaceUuid);
    result.setWorkspace(clonedWorkspaceStub);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  /**
   * Return the workspace clone result, including job result and error result.
   *
   * @param workspaceUuid - source workspace ID
   * @param jobId - ID of flight
   * @return - response with result
   */
  @Traced
  @Override
  public ResponseEntity<ApiCloneWorkspaceResult> getCloneWorkspaceResult(
      UUID workspaceUuid, String jobId) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    jobService.verifyUserAccess(jobId, userRequest, workspaceUuid);
    final ApiCloneWorkspaceResult result = fetchCloneWorkspaceResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Traced
  @Override
  public ResponseEntity<ApiRegions> listValidRegions(
      UUID workspaceUuid, ApiCloudPlatform platform) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);

    List<String> regions =
        Rethrow.onInterrupted(
            () ->
                tpsApiDispatch.listValidRegions(
                    workspaceUuid, CloudPlatform.fromApiCloudPlatform(platform)),
            "listValidRegions");

    ApiRegions apiRegions = new ApiRegions();
    apiRegions.addAll(regions);
    return new ResponseEntity<>(apiRegions, HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiWsmPolicyExplainResult> explainPolicies(
      UUID workspaceUuid, Integer depth) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceUuid, SamWorkspaceAction.READ);
    PolicyExplainResult explainResult =
        Rethrow.onInterrupted(
            () -> tpsApiDispatch.explain(workspaceUuid, depth, workspaceService, userRequest),
            "explain");

    return new ResponseEntity<>(explainResult.toApi(), HttpStatus.OK);
  }

  @Traced
  @Override
  public ResponseEntity<ApiWsmPolicyMergeCheckResult> mergeCheck(
      UUID targetWorkspaceId, ApiMergeCheckRequest requestBody) {
    UUID sourceWorkspaceId = requestBody.getWorkspaceId();
    Rethrow.onInterrupted(
        () ->
            tpsApiDispatch.getOrCreatePao(
                sourceWorkspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE),
        "getOrCreatePao");
    Rethrow.onInterrupted(
        () ->
            tpsApiDispatch.getOrCreatePao(
                targetWorkspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE),
        "getOrCreatePao");

    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    workspaceService.validateWorkspaceAndAction(
        userRequest, targetWorkspaceId, SamWorkspaceAction.READ);
    TpsPaoUpdateResult dryRunResults =
        Rethrow.onInterrupted(
            () ->
                tpsApiDispatch.mergePao(
                    targetWorkspaceId, sourceWorkspaceId, TpsUpdateMode.DRY_RUN),
            "mergePao");

    addAnyGroupMergeConflicts(targetWorkspaceId, sourceWorkspaceId, dryRunResults);

    List<UUID> resourceWithConflicts = new ArrayList<>();

    for (var platform : ApiCloudPlatform.values()) {
      HashSet<String> validRegions =
          new HashSet<>(
              Rethrow.onInterrupted(
                  () ->
                      tpsApiDispatch.listValidRegions(
                          sourceWorkspaceId, CloudPlatform.fromApiCloudPlatform(platform)),
                  "listValidRegions"));

      List<ControlledResource> existingResources =
          resourceDao.listControlledResources(
              targetWorkspaceId, CloudPlatform.fromApiCloudPlatform(platform));

      for (var existingResource : existingResources) {
        if (validRegions.stream().noneMatch(existingResource.getRegion()::equalsIgnoreCase)) {
          resourceWithConflicts.add(existingResource.getResourceId());
        }
      }
    }
    ApiWsmPolicyMergeCheckResult updateResult =
        new ApiWsmPolicyMergeCheckResult()
            .conflicts(
                TpsApiConversionUtils.apiFromTpsPaoConflictList(dryRunResults.getConflicts()))
            .resourcesWithConflict(resourceWithConflicts);

    return new ResponseEntity<>(updateResult, HttpStatus.ACCEPTED);
  }

  // Retrieve the async result or progress for clone workspace.
  private ApiCloneWorkspaceResult fetchCloneWorkspaceResult(String jobId) {
    JobApiUtils.AsyncJobResult<ApiClonedWorkspace> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, ApiClonedWorkspace.class);
    return new ApiCloneWorkspaceResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .workspace(jobResult.getResult());
  }

  /**
   * Return Pet SA credentials if available, otherwise the user credentials associated with this
   * request. It's possible to clone a workspace that has no cloud context, and thus no (GCP) pet
   * account.
   *
   * @param workspaceUuid - ID of workspace to be cloned
   * @return user or pet request
   */
  private AuthenticatedUserRequest getCloningCredentials(UUID workspaceUuid) {
    final AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    return petSaService.getWorkspacePetCredentials(workspaceUuid, userRequest).orElse(userRequest);
  }

  /**
   * Check that group policies are not changed during a merge operation. TPS allows this, but WSM
   * will need to enforce the Milestone 1 limitation of immutable groups. If groups change during a
   * merge request, WSM will add a conflict in to the TpsPaoUpdateResult.
   *
   * @param targetWorkspaceId
   * @param sourceWorkspaceId
   * @param dryRunResults
   */
  private void addAnyGroupMergeConflicts(
      UUID targetWorkspaceId, UUID sourceWorkspaceId, TpsPaoUpdateResult dryRunResults) {
    TpsPaoGetResult targetPaoPreUpdate =
        Rethrow.onInterrupted(() -> tpsApiDispatch.getPao(targetWorkspaceId), "getPao");

    HashSet<String> priorGroups =
        new HashSet<>(
            TpsUtilities.getGroupConstraintsFromInputs(
                targetPaoPreUpdate.getEffectiveAttributes()));
    HashSet<String> mergedGroups =
        new HashSet<>(
            TpsUtilities.getGroupConstraintsFromInputs(
                dryRunResults.getResultingPao().getEffectiveAttributes()));

    if (!priorGroups.equals(mergedGroups)) {
      var sourcePao =
          Rethrow.onInterrupted(() -> tpsApiDispatch.getPao(sourceWorkspaceId), "getPao");
      TpsPaoDescription targetDescription =
          new TpsPaoDescription()
              .objectId(targetWorkspaceId)
              .component(targetPaoPreUpdate.getComponent())
              .objectType(targetPaoPreUpdate.getObjectType());
      TpsPaoDescription sourceDescription =
          new TpsPaoDescription()
              .objectId(sourceWorkspaceId)
              .component(sourcePao.getComponent())
              .objectType(sourcePao.getObjectType());

      TpsPaoConflict conflict = new TpsPaoConflict();
      conflict.setConflictPao(sourceDescription);
      conflict.setTargetPao(targetDescription);
      conflict.setNamespace(TpsUtilities.TERRA_NAMESPACE);
      conflict.setName(TpsUtilities.GROUP_CONSTRAINT);
      dryRunResults.addConflictsItem(conflict);
    }
  }
}
