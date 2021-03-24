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

  public static class SamControlledResourceNames {
    public static final String CONTROLLED_USER_SHARED_RESOURCE =
        "controlled-user-shared-workspace-resource";
    public static final String CONTROLLED_USER_PRIVATE_RESOURCE =
        "controlled-user-private-workspace-resource";
    public static final String CONTROLLED_APPLICATION_SHARED_RESOURCE =
        "controlled-application-shared-workspace-resource";
    public static final String CONTROLLED_APPLICATION_PRIVATE_RESOURCE =
        "controlled-application-private-workspace-resource";

    /** Return the Sam resource name for the specified controlled resource type. */
    public static String get(AccessScopeType accessScope, ManagedByType managedBy) {
      if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED) {
        if (managedBy == ManagedByType.MANAGED_BY_USER) {
          return CONTROLLED_USER_SHARED_RESOURCE;
        } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
          return CONTROLLED_APPLICATION_SHARED_RESOURCE;
        }
      } else if (accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
        if (managedBy == ManagedByType.MANAGED_BY_USER) {
          return CONTROLLED_USER_PRIVATE_RESOURCE;
        } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
          return CONTROLLED_APPLICATION_PRIVATE_RESOURCE;
        }
      }
      throw new InternalLogicException(
          String.format(
              "Sam resource name not specified for access scope %s and ManagedByType %s",
              accessScope.toString(), managedBy.toString()));
    }

    private SamControlledResourceNames() {}
  }

  public static class SamControlledResourceCreateActions {

    public static final String CREATE_CONTROLLED_USER_SHARED = "create_controlled_user_shared";
    public static final String CREATE_CONTROLLED_USER_PRIVATE = "create_controlled_user_private";
    public static final String CREATE_CONTROLLED_APPLICATION_SHARED =
        "create_controlled_application_shared";
    public static final String CREATE_CONTROLLED_APPLICATION_PRIVATE =
        "create_controlled_application_private";

    /** Return the Sam action name for creating a specific type of controlled resource. */
    public static String get(AccessScopeType accessScope, ManagedByType managedBy) {
      if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED) {
        if (managedBy == ManagedByType.MANAGED_BY_USER) {
          return CREATE_CONTROLLED_USER_SHARED;
        } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
          return CREATE_CONTROLLED_APPLICATION_SHARED;
        }
      } else if (accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
        if (managedBy == ManagedByType.MANAGED_BY_USER) {
          return CREATE_CONTROLLED_USER_PRIVATE;
        } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
          return CREATE_CONTROLLED_APPLICATION_PRIVATE;
        }
      }
      throw new InternalLogicException(
          String.format(
              "Sam resource creation action not specified for access scope %s and ManagedByType %s",
              accessScope.toString(), managedBy.toString()));
    }

    private SamControlledResourceCreateActions() {}
  }
}
