package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.model.ApiControlledResourceCommonFields;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateUserRole;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;

/**
 * Super class for controllers containing common code. The code in here requires the @Autowired
 * beans from the @Controller classes, so it is better as a superclass rather than static methods.
 */
public class ControlledResourceControllerCommon extends ControllerCommon {
  private final ControlledResourceService controlledResourceService;

  public ControlledResourceControllerCommon(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      ControlledResourceService controlledResourceService,
      SamService samService) {
    super(authenticatedUserRequestFactory, request, samService);
    this.controlledResourceService = controlledResourceService;
  }

  public ControlledResourceFields toCommonFields(
      UUID workspaceId,
      ApiControlledResourceCommonFields apiCommonFields,
      AuthenticatedUserRequest userRequest) {

    ManagedByType managedBy = ManagedByType.fromApi(apiCommonFields.getManagedBy());
    AccessScopeType accessScopeType = AccessScopeType.fromApi(apiCommonFields.getAccessScope());
    PrivateUserRole privateUserRole =
        computePrivateUserRole(workspaceId, apiCommonFields, userRequest);

    return ControlledResourceFields.builder()
        .workspaceId(workspaceId)
        .resourceId(UUID.randomUUID())
        .name(apiCommonFields.getName())
        .description(apiCommonFields.getDescription())
        .cloningInstructions(
            CloningInstructions.fromApiModel(apiCommonFields.getCloningInstructions()))
        .iamRole(privateUserRole.getRole())
        .assignedUser(privateUserRole.getUserEmail())
        .accessScope(accessScopeType)
        .managedBy(managedBy)
        .applicationId(controlledResourceService.getAssociatedApp(managedBy, userRequest))
        .build();
  }
}
