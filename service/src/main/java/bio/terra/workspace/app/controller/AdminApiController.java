package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.AdminApi;
import bio.terra.workspace.generated.model.ApiSyncIamRolesResult;
import bio.terra.workspace.service.admin.AdminService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobService.AsyncJobResult;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AdminApiController extends ControllerBase implements AdminApi {
  private final AdminService adminService;
  private final JobService jobService;

  @Autowired
  public AdminApiController(
      AdminService adminService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      JobService jobService) {
    super(authenticatedUserRequestFactory, request, samService);
    this.adminService = adminService;
    this.jobService = jobService;
  }

  @Override
  public ResponseEntity<ApiSyncIamRolesResult> syncIamRoles() {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    SamRethrow.onInterrupted(
        () -> getSamService().checkAdminAuthz(userRequest),
        "check whether the user has admin access");
    String jobId = adminService.syncIamRoleForAllGcpProjects(userRequest);

    ApiSyncIamRolesResult response = fetchSyncIamRolesResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  private ApiSyncIamRolesResult fetchSyncIamRolesResult(String jobId) {
    AsyncJobResult<Void> jobResult = jobService.retrieveAsyncJobResult(jobId, null);
    return new ApiSyncIamRolesResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }
}
