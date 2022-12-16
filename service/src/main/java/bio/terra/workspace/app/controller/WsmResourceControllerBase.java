package bio.terra.workspace.app.controller;

import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.model.WsmResourceApiFields;
import java.util.UUID;
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

  public WsmResourceApiFields getWsmResourceApiFields(UUID workspaceUuid, UUID resourceId) {
    return WsmResourceApiFields.build(workspaceActivityLogService, workspaceUuid, resourceId);
  }
}
