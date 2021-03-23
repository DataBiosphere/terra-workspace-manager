package bio.terra.workspace.service.iam.model;

import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;

public class SamConstants {

  public static final String SAM_WORKSPACE_RESOURCE = "workspace";
  public static final String SAM_WORKSPACE_READ_ACTION = "read";
  public static final String SAM_WORKSPACE_WRITE_ACTION = "write";
  public static final String SAM_WORKSPACE_DELETE_ACTION = "delete";
  public static final String SAM_WORKSPACE_READ_IAM_ACTION = "read_policies";
  public static final String SPEND_PROFILE_RESOURCE = "spend-profile";
  public static final String SPEND_PROFILE_LINK_ACTION = "link";
  public static final String SAM_CONTROLLED_USER_SHARED_RESOURCE =
      "controlled-user-shared-workspace-resource";
  public static final String SAM_CREATE_CONTROLLED_USER_SHARED_ACTION =
      "create_controlled_user_shared";
  public static final String SAM_CONTROLLED_USER_PRIVATE_RESOURCE =
      "controlled-user-private-workspace-resource";
  public static final String SAM_CREATE_CONTROLLED_USER_PRIVATE_ACTION =
      "create_controlled_user_private";
  public static final String SAM_CONTROLLED_APPLICATION_SHARED_RESOURCE =
      "controlled-application-shared-workspace-resource";
  public static final String SAM_CREATE_CONTROLLED_APPLICATION_SHARED_ACTION =
      "create_controlled_application_shared";
  public static final String SAM_CONTROLLED_APPLICATION_PRIVATE_RESOURCE =
      "controlled-application-private-workspace-resource";
  public static final String SAM_CREATE_CONTROLLED_APPLICATION_PRIVATE_ACTION =
      "create_controlled_application_private";

  /** Return the Sam resource name for the specified controlled resource type. */
  public static String samControlledResourceType(
      AccessScopeType accessScope, ManagedByType managedBy) {
    if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED) {
      if (managedBy == ManagedByType.MANAGED_BY_USER) {
        return SamConstants.SAM_CONTROLLED_USER_SHARED_RESOURCE;
      } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
        return SamConstants.SAM_CONTROLLED_APPLICATION_SHARED_RESOURCE;
      }
    } else if (accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      if (managedBy == ManagedByType.MANAGED_BY_USER) {
        return SamConstants.SAM_CONTROLLED_USER_PRIVATE_RESOURCE;
      } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
        return SamConstants.SAM_CONTROLLED_APPLICATION_PRIVATE_RESOURCE;
      }
    }
    throw new InternalLogicException(
        String.format(
            "Sam resource name not specified for access scope %s and ManagedByType %s",
            accessScope.toString(), managedBy.toString()));
  }

  /** Return the Sam action name for creating a specific type of controlled resource. */
  public static String samCreateControlledResourceAction(
      AccessScopeType accessScope, ManagedByType managedBy) {
    if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED) {
      if (managedBy == ManagedByType.MANAGED_BY_USER) {
        return SamConstants.SAM_CREATE_CONTROLLED_USER_SHARED_ACTION;
      } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
        return SamConstants.SAM_CREATE_CONTROLLED_APPLICATION_SHARED_ACTION;
      }
    } else if (accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      if (managedBy == ManagedByType.MANAGED_BY_USER) {
        return SamConstants.SAM_CREATE_CONTROLLED_USER_PRIVATE_ACTION;
      } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
        return SamConstants.SAM_CREATE_CONTROLLED_APPLICATION_PRIVATE_ACTION;
      }
    }
    throw new InternalLogicException(
        String.format(
            "Sam resource creation action not specified for access scope %s and ManagedByType %s",
            accessScope.toString(), managedBy.toString()));
  }
}
