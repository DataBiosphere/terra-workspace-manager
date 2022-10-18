package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.AdminApi;
import bio.terra.workspace.service.admin.AdminService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AdminApiController extends ControllerBase implements AdminApi {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final AdminService adminService;

  @Autowired
  public AdminApiController(
      AdminService adminService,
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService) {
    super(authenticatedUserRequestFactory, request, samService);
    this.adminService = adminService;
  }

  @Override
  public ResponseEntity<Void> syncIamRoles() {
    logger.error("sync iam roles");
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    SamRethrow.onInterrupted(
        () -> getSamService().checkAdminAuthz(userRequest),
        "check whether the user has admin access");
    adminService.syncIamRoleForAllGcpProjects(userRequest);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
