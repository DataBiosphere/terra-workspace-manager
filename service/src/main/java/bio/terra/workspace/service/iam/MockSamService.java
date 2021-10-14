package bio.terra.workspace.service.iam;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.sam.exception.SamBadRequestException;
import bio.terra.workspace.app.configuration.external.AzureState;
import bio.terra.workspace.db.IamDao;
import bio.terra.workspace.db.IamDao.PocUser;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest.AuthType;
import bio.terra.workspace.service.iam.model.RoleBinding;
import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MockSamService {
  private final IamDao iamDao;
  private final AzureState azureState;

  @Autowired
  public MockSamService(IamDao iamDao, AzureState azureState) {
    this.iamDao = iamDao;
    this.azureState = azureState;
  }

  /**
   * Helper method to decide whether or not to use the mock.
   *
   * @param userRequest incoming user auth
   * @return true to use the mock
   */
  public boolean useMock(AuthenticatedUserRequest userRequest) {
    return (azureState.isEnabled() && userRequest.getAuthType() == AuthType.BASIC);
  }

  public void createWorkspaceWithDefaults(AuthenticatedUserRequest userRequest, UUID id) {
    // Grant the user owner on the resource
    iamDao.grantRole(id, WsmIamRole.OWNER, new PocUser(userRequest));
  }

  public void deleteControlledResource(
      ControlledResource resource, AuthenticatedUserRequest userRequest) {
    // Mock does not maintain any state for controlled resources
  }

  public void createControlledResource(
      ControlledResource resource, AuthenticatedUserRequest userRequest) {
    // Mock does not maintain any state for controlled resources
  }

  public void deleteWorkspace(AuthenticatedUserRequest userRequest, UUID id) {
    // I don't think we need an access check here. There is one in the logic already.
    iamDao.deleteWorkspace(id);
  }

  public void grantWorkspaceRole(
      UUID workspaceId, AuthenticatedUserRequest userRequest, WsmIamRole role, String email) {
    PocUser pocUser = getPocUserFromEmail(email);
    iamDao.grantRole(workspaceId, role, pocUser);
  }

  public List<RoleBinding> listRoleBindings(UUID workspaceId) {
    List<RoleBinding> roleBindings = new ArrayList<>();
    roleBindings.add(getOneRoleBinding(workspaceId, WsmIamRole.OWNER));
    roleBindings.add(getOneRoleBinding(workspaceId, WsmIamRole.WRITER));
    roleBindings.add(getOneRoleBinding(workspaceId, WsmIamRole.READER));
    roleBindings.add(getOneRoleBinding(workspaceId, WsmIamRole.APPLICATION));
    return roleBindings;
  }

  private RoleBinding getOneRoleBinding(UUID workspaceId, WsmIamRole role) {
    List<String> users = iamDao.listRoleUsers(workspaceId, role);
    return RoleBinding.builder().role(role).users(users).build();
  }

  public List<UUID> listWorkspaceIds(AuthenticatedUserRequest userRequest) {
    return iamDao.listAccessible(new PocUser(userRequest));
  }

  public boolean isAuthorized(
      AuthenticatedUserRequest userRequest,
      String iamResourceType,
      String resourceId,
      String action) {

    String userId = userRequest.getSubjectId();

    // SpendProfile - everyone has access. Wheeeee!
    if (StringUtils.equals(iamResourceType, SamResource.SPEND_PROFILE)) {
      return true;
    }

    // Workspace - map action to role checks in the database
    if (StringUtils.equals(iamResourceType, SamResource.WORKSPACE)) {
      UUID workspaceId = UUID.fromString(resourceId);
      switch (action) {
          // workspace owner actions
        case SamWorkspaceAction.OWN:
        case SamWorkspaceAction.READ_IAM:
        case SamWorkspaceAction.DELETE:
          return iamDao.roleCheck(workspaceId, List.of(WsmIamRole.OWNER), userId);

          // workspace owner or writer actions
        case SamWorkspaceAction.WRITE:
        case SamWorkspaceAction.CREATE_REFERENCE:
        case SamWorkspaceAction.UPDATE_REFERENCE:
        case SamWorkspaceAction.DELETE_REFERENCE:
        case SamWorkspaceAction.CREATE_CONTROLLED_USER_SHARED:
        case SamWorkspaceAction.CREATE_CONTROLLED_APPLICATION_SHARED:
        case SamWorkspaceAction.CREATE_CONTROLLED_USER_PRIVATE:
        case SamWorkspaceAction.CREATE_CONTROLLED_APPLICATION_PRIVATE:
          return iamDao.roleCheck(
              workspaceId, List.of(WsmIamRole.OWNER, WsmIamRole.WRITER), userId);

          // workspace reader actions
        case SamWorkspaceAction.READ:
          return iamDao.roleCheck(
              workspaceId, List.of(WsmIamRole.OWNER, WsmIamRole.WRITER, WsmIamRole.READER), userId);

        default:
          // All share_policy variants require owner
          if (StringUtils.startsWith(action, "share_policy:")) {
            return iamDao.roleCheck(workspaceId, List.of(WsmIamRole.OWNER), userId);
          }
          throw new UnauthorizedException("Unexpected action on workspace: " + action);
      }
    }

    // User Shared Resource - map action to workspace roles
    // Workspace OWNER and WRITER -> editor/writer/reader role on resource = all actions on resource
    // Workspace READER -> reader role on resource = read action on resource
    if (StringUtils.equals(
        iamResourceType, ControlledResourceCategory.USER_SHARED.getSamResourceName())) {

      UUID workspaceId =
          iamDao
              .getWorkspaceIdFromResourceId(resourceId)
              .orElseThrow(() -> new IllegalArgumentException("unknown resource id"));

      switch (action) {
        case SamControlledResourceActions.EDIT_ACTION:
        case SamControlledResourceActions.DELETE_ACTION:
        case SamControlledResourceActions.WRITE_ACTION:
          return iamDao.roleCheck(
              workspaceId, List.of(WsmIamRole.OWNER, WsmIamRole.WRITER), userId);

        case SamControlledResourceActions.READ_ACTION:
          return iamDao.roleCheck(
              workspaceId, List.of(WsmIamRole.OWNER, WsmIamRole.WRITER, WsmIamRole.READER), userId);

        default:
          // All share_policy variants require owner
          if (StringUtils.startsWith(action, "share_policy:")) {
            return iamDao.roleCheck(workspaceId, List.of(WsmIamRole.OWNER), userId);
          }

          throw new IllegalStateException("Unexpected action on resource: " + action);
      }
    }

    // For now, we do not support other resource types in the Azure PoC
    throw new IllegalStateException("We only support user shared resources in the PoC right now");
  }

  public void removeWorkspaceRole(
      UUID workspaceId, AuthenticatedUserRequest userRequest, WsmIamRole role, String email) {
    PocUser pocUser = getPocUserFromEmail(email);
    iamDao.revokeRole(workspaceId, role, pocUser.getUserId());
  }

  private PocUser getPocUserFromEmail(String email) {
    return iamDao
        .getUserFromEmail(email)
        .orElseThrow(() -> new SamBadRequestException("User not found: " + email));
  }

  public String syncPolicyOnObject(
      String resourceTypeName,
      String resourceId,
      String policyName,
      AuthenticatedUserRequest userRequest) {
    return "mock-group";
  }
}
