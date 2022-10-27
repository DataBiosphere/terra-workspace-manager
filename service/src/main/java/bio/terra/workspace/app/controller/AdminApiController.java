package bio.terra.workspace.app.controller;

import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.generated.controller.AdminApi;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.admin.AdminService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AdminApiController extends ControllerBase implements AdminApi {
  private final AdminService adminService;
  private final JobApiUtils jobApiUtils;

  @Autowired
  public AdminApiController(
      AdminService adminService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      JobApiUtils jobApiUtils) {
    super(authenticatedUserRequestFactory, request, samService);
    this.adminService = adminService;
    this.jobApiUtils = jobApiUtils;
  }

  @Override
  public ResponseEntity<ApiJobResult> syncIamRoles(Boolean wetRun) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    SamRethrow.onInterrupted(
        () -> getSamService().checkAdminAuthz(userRequest),
        "check whether the user has admin access");

    String jobId =
        adminService.syncIamRoleForAllGcpProjects(userRequest, Boolean.TRUE.equals(wetRun));
    var response = jobApiUtils.fetchJobResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }
}
