package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.WorkspaceApplicationApi;
import bio.terra.workspace.generated.model.ApiWorkspaceApplicationDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceApplicationDescriptionList;
import bio.terra.workspace.generated.model.ApiWorkspaceApplicationState;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WorkspaceApplicationApiController implements WorkspaceApplicationApi {
  private static final Logger logger =
      LoggerFactory.getLogger(WorkspaceApplicationApiController.class);
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;
  private final WsmApplicationService appService;

  @Autowired
  public WorkspaceApplicationApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      WsmApplicationService appService) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
    this.appService = appService;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @Override
  public ResponseEntity<ApiWorkspaceApplicationDescription> disableWorkspaceApplication(
      @PathVariable("workspaceId") UUID workspaceId,
      @PathVariable("applicationId") UUID applicationId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    WsmWorkspaceApplication wsmApp =
        appService.disableWorkspaceApplication(userRequest, workspaceId, applicationId);
    ApiWorkspaceApplicationDescription response = makeApiWorkspaceApplication(wsmApp);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiWorkspaceApplicationDescription> enableWorkspaceApplication(
      @PathVariable("workspaceId") UUID workspaceId,
      @PathVariable("applicationId") UUID applicationId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    WsmWorkspaceApplication wsmApp =
        appService.enableWorkspaceApplication(userRequest, workspaceId, applicationId);
    ApiWorkspaceApplicationDescription response = makeApiWorkspaceApplication(wsmApp);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiWorkspaceApplicationDescription> getWorkspaceApplication(
      @PathVariable("workspaceId") UUID workspaceId,
      @PathVariable("applicationId") UUID applicationId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    WsmWorkspaceApplication wsmApp =
        appService.getWorkspaceApplication(userRequest, workspaceId, applicationId);
    ApiWorkspaceApplicationDescription response = makeApiWorkspaceApplication(wsmApp);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiWorkspaceApplicationDescriptionList> listWorkspaceApplications(
      @PathVariable("workspaceId") UUID workspaceId,
      @Min(0) @Valid Integer offset,
      @Min(1) @Valid Integer limit) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    List<WsmWorkspaceApplication> wsmApps =
        appService.listWorkspaceApplications(userRequest, workspaceId, offset, limit);
    var response = new ApiWorkspaceApplicationDescriptionList();
    for (WsmWorkspaceApplication wsmApp : wsmApps) {
      response.addApplicationsItem(makeApiWorkspaceApplication(wsmApp));
    }
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private ApiWorkspaceApplicationDescription makeApiWorkspaceApplication(
      WsmWorkspaceApplication wsmApp) {
    return new ApiWorkspaceApplicationDescription()
        .workspaceId(wsmApp.getWorkspaceId())
        .applicationId(wsmApp.getApplication().getApplicationId())
        .displayName(wsmApp.getApplication().getDisplayName())
        .description(wsmApp.getApplication().getDescription())
        .applicationState(wsmApp.getApplication().getState().toApi())
        .workspaceApplicationState(
            (wsmApp.isEnabled()
                ? ApiWorkspaceApplicationState.ENABLED
                : ApiWorkspaceApplicationState.DISABLED));
  }
}
