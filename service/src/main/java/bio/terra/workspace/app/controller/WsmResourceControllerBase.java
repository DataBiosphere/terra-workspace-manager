package bio.terra.workspace.app.controller;

import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceApiFields;
import javax.servlet.http.HttpServletRequest;

public class WsmResourceControllerBase extends ControllerBase {

  private final WorkspaceActivityLogService workspaceActivityLogService;

  public WsmResourceControllerBase(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      WorkspaceActivityLogService workspaceActivityLogService) {
    super(authenticatedUserRequestFactory, request, samService);
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  public WsmResourceApiFields getWsmResourceApiFields(WsmResource resource) {
    return WsmResourceApiFields.build(workspaceActivityLogService, resource);
  }
}
