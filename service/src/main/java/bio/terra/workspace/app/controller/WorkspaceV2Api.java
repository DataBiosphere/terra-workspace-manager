package bio.terra.workspace.app.controller;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertApiPropertyToMap;

import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.workspace.app.controller.shared.ControllerUtils;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.app.controller.shared.WorkspaceApiUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.generated.model.ApiDeleteCloudContextV2Request;
import bio.terra.workspace.generated.model.ApiDeleteWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * This class contains the implementation of the WorkspaceV2 endpoints. They are directly forwarded
 * from the controller to here.
 */
@Component
public class WorkspaceV2Api {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceV2Api.class);
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final FeatureService featureService;
  private final JobApiUtils jobApiUtils;
  private final JobService jobService;
  private final HttpServletRequest request;
  private final SamService samService;
  private final WorkspaceApiUtils workspaceApiUtils;
  private final WorkspaceService workspaceService;

  public WorkspaceV2Api(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      FeatureService featureService,
      JobApiUtils jobApiUtils,
      JobService jobService,
      HttpServletRequest request,
      SamService samService,
      WorkspaceApiUtils workspaceApiUtils,
      WorkspaceService workspaceService) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.featureService = featureService;
    this.jobApiUtils = jobApiUtils;
    this.jobService = jobService;
    this.request = request;
    this.samService = samService;
    this.workspaceApiUtils = workspaceApiUtils;
    this.workspaceService = workspaceService;
  }

  public ResponseEntity<ApiCreateWorkspaceV2Result> createWorkspaceV2(
      ApiCreateWorkspaceV2Request body) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    logger.info(
        "Creating workspace {} and {} cloud context for {} subject {}",
        body.getId(),
        (body.getCloudPlatform() == null ? "no" : body.getCloudPlatform()),
        userRequest.getEmail(),
        userRequest.getSubjectId());
    String jobId = body.getJobControl().getId();

    // If we are creating a cloud context (cloudPlatform is present),
    // then the spend profile must be valid and the user must have
    // permission to link to it.
    CloudPlatform cloudPlatform = null;
    SpendProfile spendProfile = null;
    SpendProfileId spendProfileId = null;
    ApiCloudPlatform apiCloudPlatform = body.getCloudPlatform();
    if (apiCloudPlatform != null) {
      ControllerValidationUtils.validateCloudPlatform(apiCloudPlatform);
      cloudPlatform = CloudPlatform.fromApiCloudPlatform(apiCloudPlatform);

      if (cloudPlatform == CloudPlatform.AWS) {
        featureService.featureEnabledCheck(
            FeatureService.AWS_ENABLED,
            samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest));
      }

      spendProfile =
          workspaceApiUtils.validateSpendProfilePermission(userRequest, body.getSpendProfile());
      if (spendProfile == null) {
        throw new MissingRequiredFieldsException(
            "To create a cloud context you must provide both cloudPlatform and spendProfile");
      }
      spendProfileId = spendProfile.id();
    }

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

    workspaceService.createWorkspaceV2(
        workspace,
        policies,
        body.getApplicationIds(),
        cloudPlatform,
        spendProfile,
        body.getProjectOwnerGroupId(),
        jobId,
        userRequest);

    ApiCreateWorkspaceV2Result result = fetchCreateWorkspaceV2Result(jobId);
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
  }

  private ApiCreateWorkspaceV2Result fetchCreateWorkspaceV2Result(String jobId) {
    JobApiUtils.AsyncJobResult<UUID> jobResult =
        jobApiUtils.retrieveAsyncJobResult(jobId, UUID.class);

    UUID workspaceUuid = null;
    if (jobResult.getJobReport().getStatus().equals(ApiJobReport.StatusEnum.SUCCEEDED)) {
      workspaceUuid = jobResult.getResult();
    }

    return new ApiCreateWorkspaceV2Result()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport())
        .workspaceId(workspaceUuid);
  }

  public ResponseEntity<ApiCreateWorkspaceV2Result> getCreateWorkspaceV2Result(String jobId) {
    // No access control on this because it has no containing access-controlled resource
    ApiCreateWorkspaceV2Result result = fetchCreateWorkspaceV2Result(jobId);
    return new ResponseEntity<>(
        result, ControllerUtils.getAsyncResponseCode(result.getJobReport()));
  }

  public ResponseEntity<ApiJobResult> deleteCloudContextV2(
      UUID workspaceId, ApiCloudPlatform cloudContext, ApiDeleteCloudContextV2Request body) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    ControllerValidationUtils.validateCloudPlatform(cloudContext);
    String jobId = body.getJobControl().getId();

    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamWorkspaceAction.DELETE);

    workspaceService.deleteCloudContextAsync(
        workspace,
        CloudPlatform.fromApiCloudPlatform(cloudContext),
        userRequest,
        jobId,
        ControllerUtils.getAsyncResultEndpoint(request, jobId));

    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(
        response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }

  public ResponseEntity<ApiJobResult> deleteWorkspaceV2(
      UUID workspaceId, ApiDeleteWorkspaceV2Request body) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    String jobId = body.getJobControl().getId();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceId, SamConstants.SamWorkspaceAction.DELETE);

    logger.info("Starting delete of workspace {} for {}", workspaceId, userRequest.getEmail());
    workspaceService.deleteWorkspaceAsync(
        workspace,
        userRequest,
        jobId,
        ControllerUtils.getAsyncResultEndpoint(request, jobId, "delete-result"));

    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(
        response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }

  public ResponseEntity<ApiJobResult> getDeleteCloudContextV2Result(
      UUID workspaceId, String jobId) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(
        response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }

  public ResponseEntity<ApiJobResult> getDeleteWorkspaceV2Result(UUID workspaceId, String jobId) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    try {
      jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    } catch (WorkspaceNotFoundException e) {
      // For delete workspace, we treat not found as accessible
    }
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(
        response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }
}
