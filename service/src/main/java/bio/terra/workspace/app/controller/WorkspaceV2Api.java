package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.controller.shared.ControllerUtils;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.controller.WorkspaceApi;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreateCloudContextResult;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiCreateWorkspaceV2Result;
import bio.terra.workspace.generated.model.ApiDeleteCloudContextV2Request;
import bio.terra.workspace.generated.model.ApiDeleteWorkspaceV2Request;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.CloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * This class contains the implementation of the WorkspaceV2 endpoints.
 * They are directly forwarded from the controller to here.
 */
@Component
public class WorkspaceV2Api {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceV2Api.class);
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final JobApiUtils jobApiUtils;
  private final JobService jobService;
  private final HttpServletRequest request;
  private final WorkspaceService workspaceService;

  public WorkspaceV2Api(
    AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
    JobApiUtils jobApiUtils,
    JobService jobService,
    HttpServletRequest request,
    WorkspaceService workspaceService) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.jobApiUtils = jobApiUtils;
    this.jobService = jobService;
    this.request = request;
    this.workspaceService = workspaceService;
  }

  public ResponseEntity<ApiCreateWorkspaceV2Result> createWorkspaceV2(ApiCreateWorkspaceV2Request body) {
    throw new FeatureNotSupportedException("Not implemented yet");
  }

  public ResponseEntity<ApiJobResult> deleteCloudContextV2(UUID workspaceId, ApiCloudPlatform cloudContext, ApiDeleteCloudContextV2Request body) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    ControllerValidationUtils.validateCloudPlatform(cloudContext);
    String jobId = body.getJobControl().getId();

    Workspace workspace =
      workspaceService.validateMcWorkspaceAndAction(userRequest, workspaceId, SamConstants.SamWorkspaceAction.WRITE);

    workspaceService.deleteCloudContextAsync(
      workspace, CloudPlatform.fromApiCloudPlatform(cloudContext),
      userRequest,
      jobId,
      ControllerUtils.getAsyncResultEndpoint(request, jobId));

    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }

  public ResponseEntity<ApiJobResult> deleteWorkspaceV2(UUID workspaceId, ApiDeleteWorkspaceV2Request body) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    String jobId = body.getJobControl().getId();
    Workspace workspace =
      workspaceService.validateMcWorkspaceAndAction(userRequest, workspaceId, SamConstants.SamWorkspaceAction.WRITE);

    logger.info("Starting delete of workspace {} for {}", workspaceId, userRequest.getEmail());
    workspaceService.deleteWorkspaceAsync(workspace, userRequest, jobId,ControllerUtils.getAsyncResultEndpoint(request, jobId, "delete-result"));

    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }

  public ResponseEntity<ApiCreateWorkspaceV2Result> getCreateWorkspaceV2Result(String jobId) {
    throw new FeatureNotSupportedException("Not implemented yet");
  }

  public ResponseEntity<ApiJobResult> getDeleteCloudContextV2Result(UUID workspaceId, String jobId) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }

  public ResponseEntity<ApiJobResult> getDeleteWorkspaceV2Result(UUID workspaceId, String jobId) {
    AuthenticatedUserRequest userRequest = authenticatedUserRequestFactory.from(request);
    try {
      jobService.verifyUserAccess(jobId, userRequest, workspaceId);
    } catch (WorkspaceNotFoundException e) {
      // For delete workspace, we treat not found as accessible
    }
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, ControllerUtils.getAsyncResponseCode(response.getJobReport()));
  }
}
