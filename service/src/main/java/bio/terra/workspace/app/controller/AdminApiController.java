package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.generated.controller.AdminApi;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.admin.AdminService;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AdminApiController extends ControllerBase implements AdminApi {
  private final AdminService adminService;

  @Autowired
  public AdminApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      FeatureConfiguration features,
      FeatureService featureService,
      SamService samService,
      JobService jobService,
      JobApiUtils jobApiUtils,
      AdminService adminService) {
    super(
        authenticatedUserRequestFactory,
        request,
        samService,
        features,
        featureService,
        jobService,
        jobApiUtils);
    this.adminService = adminService;
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiJobResult> syncIamRoles(Boolean wetRun) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Rethrow.onInterrupted(
        () -> samService.checkAdminAuthz(userRequest), "check whether the user has admin access");

    String jobId =
        adminService.syncIamRoleForAllGcpProjects(userRequest, Boolean.TRUE.equals(wetRun));
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiJobResult> getSyncIamRolesResult(String jobId) {
    return getApiJobResult(jobId);
  }

  private ResponseEntity<ApiJobResult> getApiJobResult(String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Rethrow.onInterrupted(
        () -> samService.checkAdminAuthz(userRequest), "check whether the user has admin access");
    ApiJobResult response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiJobResult> getBackfillControlledResourcesRegionsResult(String jobId) {
    return getApiJobResult(jobId);
  }
}
