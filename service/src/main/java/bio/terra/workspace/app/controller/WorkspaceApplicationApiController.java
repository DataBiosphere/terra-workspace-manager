package bio.terra.workspace.app.controller;

import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.WorkspaceApplicationApi;
import bio.terra.workspace.generated.model.ApiWorkspaceApplicationDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceApplicationDescriptionList;
import bio.terra.workspace.generated.model.ApiWorkspaceApplicationState;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WorkspaceApplicationApiController implements WorkspaceApplicationApi {
  private final AuthenticatedUserRequestFactory authenticatedUserRequestFactory;
  private final HttpServletRequest request;
  private final WsmApplicationService appService;
  private final WorkspaceService workspaceService;

  @Autowired
  public WorkspaceApplicationApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      WsmApplicationService appService,
      WorkspaceService workspaceService) {
    this.authenticatedUserRequestFactory = authenticatedUserRequestFactory;
    this.request = request;
    this.appService = appService;
    this.workspaceService = workspaceService;
  }

  private AuthenticatedUserRequest getAuthenticatedInfo() {
    return authenticatedUserRequestFactory.from(request);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiWorkspaceApplicationDescription> disableWorkspaceApplication(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @PathVariable("applicationId") String applicationId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.OWN);
    WsmWorkspaceApplication wsmApp =
        appService.disableWorkspaceApplication(userRequest, workspace, applicationId);
    ApiWorkspaceApplicationDescription response = makeApiWorkspaceApplication(wsmApp);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiWorkspaceApplicationDescription> enableWorkspaceApplication(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @PathVariable("applicationId") String applicationId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.OWN);
    WsmWorkspaceApplication wsmApp =
        appService.enableWorkspaceApplication(userRequest, workspace, applicationId);
    ApiWorkspaceApplicationDescription response = makeApiWorkspaceApplication(wsmApp);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiWorkspaceApplicationDescription> getWorkspaceApplication(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @PathVariable("applicationId") String applicationId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);
    WsmWorkspaceApplication wsmApp = appService.getWorkspaceApplication(workspace, applicationId);
    ApiWorkspaceApplicationDescription response = makeApiWorkspaceApplication(wsmApp);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @WithSpan
  @Override
  public ResponseEntity<ApiWorkspaceApplicationDescriptionList> listWorkspaceApplications(
      @PathVariable("workspaceId") UUID workspaceUuid,
      @Valid @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
      @Valid @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ControllerValidationUtils.validatePaginationParams(offset, limit);
    Workspace workspace =
        workspaceService.validateMcWorkspaceAndAction(
            userRequest, workspaceUuid, SamConstants.SamWorkspaceAction.READ);

    List<WsmWorkspaceApplication> wsmApps =
        appService.listWorkspaceApplications(workspace, offset, limit);
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
