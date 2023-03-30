package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.ControlledResourceSyncMapping;
import bio.terra.workspace.service.resource.controlled.ControlledResourceSyncMapping.SyncMapping;
import java.util.List;

/**
 * A combination of AccessScopeType and ManagedByType. These categories handle IAM slightly
 * differently, so they're pulled into a separate enum here for convenience.
 */
public enum ControlledResourceCategory {
  USER_SHARED(
      AccessScopeType.ACCESS_SCOPE_SHARED,
      ManagedByType.MANAGED_BY_USER,
      SamConstants.SamResource.CONTROLLED_USER_SHARED,
      SamConstants.SamWorkspaceAction.CREATE_CONTROLLED_USER_SHARED,
      ControlledResourceSyncMapping.USER_SHARED_MAPPING),
  USER_PRIVATE(
      AccessScopeType.ACCESS_SCOPE_PRIVATE,
      ManagedByType.MANAGED_BY_USER,
      SamConstants.SamResource.CONTROLLED_USER_PRIVATE,
      SamConstants.SamWorkspaceAction.CREATE_CONTROLLED_USER_PRIVATE,
      ControlledResourceSyncMapping.USER_PRIVATE_MAPPING),
  APPLICATION_SHARED(
      AccessScopeType.ACCESS_SCOPE_SHARED,
      ManagedByType.MANAGED_BY_APPLICATION,
      SamConstants.SamResource.CONTROLLED_APPLICATION_SHARED,
      SamConstants.SamWorkspaceAction.CREATE_CONTROLLED_APPLICATION_SHARED,
      ControlledResourceSyncMapping.APPLICATION_SHARED_MAPPING),
  APPLICATION_PRIVATE(
      AccessScopeType.ACCESS_SCOPE_PRIVATE,
      ManagedByType.MANAGED_BY_APPLICATION,
      SamConstants.SamResource.CONTROLLED_APPLICATION_PRIVATE,
      SamConstants.SamWorkspaceAction.CREATE_CONTROLLED_APPLICATION_PRIVATE,
      ControlledResourceSyncMapping.APPLICATION_PRIVATE_MAPPING);

  private final AccessScopeType accessScopeType;
  private final ManagedByType managedByType;
  private final String samResourceName;
  private final String samCreateResourceAction;
  private final List<SyncMapping> syncMappings;

  ControlledResourceCategory(
      AccessScopeType accessScopeType,
      ManagedByType managedByType,
      String samResourceName,
      String samCreateResourceAction,
      List<SyncMapping> syncMappings) {
    this.accessScopeType = accessScopeType;
    this.managedByType = managedByType;
    this.samResourceName = samResourceName;
    this.samCreateResourceAction = samCreateResourceAction;
    this.syncMappings = syncMappings;
  }

  public List<SyncMapping> getSyncMappings() {
    return syncMappings;
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
            accessScopeType, managedByType.toString()));
  }
}
