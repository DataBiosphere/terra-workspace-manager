package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.mappings.ControlledResourceInheritanceMapping;
import com.google.common.collect.Multimap;

/**
 * A combination of AccessScopeType and ManagedByType. These categories handle IAM slightly
 * differently, so they're pulled into a separate enum here for convenience.
 */
public enum ControlledResourceCategory {
  USER_SHARED(
      AccessScopeType.ACCESS_SCOPE_SHARED,
      ManagedByType.MANAGED_BY_USER,
      "controlled-user-shared-workspace-resource",
      "create_controlled_user_shared",
      ControlledResourceInheritanceMapping.USER_SHARED_MAPPING),
  USER_PRIVATE(
      AccessScopeType.ACCESS_SCOPE_PRIVATE,
      ManagedByType.MANAGED_BY_USER,
      "controlled-user-private-workspace-resource",
      "create_controlled_user_private",
      ControlledResourceInheritanceMapping.USER_PRIVATE_MAPPING),
  APPLICATION_SHARED(
      AccessScopeType.ACCESS_SCOPE_SHARED,
      ManagedByType.MANAGED_BY_APPLICATION,
      "controlled-application-shared-workspace-resource",
      "create_controlled_application_shared",
      ControlledResourceInheritanceMapping.APPLICATION_SHARED_MAPPING),
  APPLICATION_PRIVATE(
      AccessScopeType.ACCESS_SCOPE_PRIVATE,
      ManagedByType.MANAGED_BY_APPLICATION,
      "controlled-application-private-workspace-resource",
      "create_controlled_application_private",
      ControlledResourceInheritanceMapping.APPLICATION_PRIVATE_MAPPING);

  private final AccessScopeType accessScopeType;
  private final ManagedByType managedByType;
  private final String samResourceName;
  private final String samCreateResourceAction;
  private final Multimap<WsmIamRole, ControlledResourceIamRole> inheritanceMapping;

  ControlledResourceCategory(
      AccessScopeType accessScopeType,
      ManagedByType managedByType,
      String samResourceName,
      String samCreateResourceAction,
      Multimap<WsmIamRole, ControlledResourceIamRole> inheritanceMapping) {
    this.accessScopeType = accessScopeType;
    this.managedByType = managedByType;
    this.samResourceName = samResourceName;
    this.samCreateResourceAction = samCreateResourceAction;
    this.inheritanceMapping = inheritanceMapping;
  }

  public Multimap<WsmIamRole, ControlledResourceIamRole> getInheritanceMapping() {
    return inheritanceMapping;
  }

  public AccessScopeType getAccessScopeType() {
    return accessScopeType;
  }

  public ManagedByType getManagedByType() {
    return managedByType;
  }

  public String getSamResourceName() {
    return samResourceName;
  }

  public String getSamCreateResourceAction() {
    return samCreateResourceAction;
  }

  public static ControlledResourceCategory get(
      AccessScopeType accessScopeType, ManagedByType managedByType) {
    switch (accessScopeType) {
      case ACCESS_SCOPE_SHARED:
        switch (managedByType) {
          case MANAGED_BY_USER:
            return USER_SHARED;
          case MANAGED_BY_APPLICATION:
            return APPLICATION_SHARED;
          default:
            break;
        }
        break;
      case ACCESS_SCOPE_PRIVATE:
        switch (managedByType) {
          case MANAGED_BY_USER:
            return USER_PRIVATE;
          case MANAGED_BY_APPLICATION:
            return APPLICATION_PRIVATE;
          default:
            break;
        }
        break;
    }
    throw new IllegalStateException(
        String.format(
            "Unrecognized resource category: AccessScopeType %s and ManagedByType %s",
            accessScopeType.toString(), managedByType.toString()));
  }
}
