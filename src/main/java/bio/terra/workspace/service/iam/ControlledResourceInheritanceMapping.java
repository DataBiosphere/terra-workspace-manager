package bio.terra.workspace.service.iam;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A static mapping of workspace-level IAM roles to resource-level IAM roles.
 *
 * <p>Currently, Sam's implementation of hierarchical resources does not support syncing inherited
 * roles or policies to groups. We need this functionality for WSM cloud sync, so this mapping must
 * live in WSM instead of in Sam's resource definition.
 *
 * <p>This mapping is slightly different for each category of controlled resource (user-shared,
 * user-private, app-shared, app-private), so this class holds four separate maps.
 */
public class ControlledResourceInheritanceMapping {
  // Currently, shared resources have the same permission inheritance regardless of whether they
  // are user- or application-controlled. This list is pulled out separately as a convenience.
  private static ImmutableMap<WsmIamRole, ImmutableList<ControlledResourceIamRole>>
      SHARED_RESOURCE_MAPPING =
          ImmutableMap.of(
              WsmIamRole.OWNER,
              ImmutableList.of(
                  ControlledResourceIamRole.EDITOR,
                  ControlledResourceIamRole.WRITER,
                  ControlledResourceIamRole.READER),
              WsmIamRole.WRITER,
              ImmutableList.of(
                  ControlledResourceIamRole.EDITOR,
                  ControlledResourceIamRole.WRITER,
                  ControlledResourceIamRole.READER),
              // Applications are granted permissions individually, not as a role.
              WsmIamRole.APPLICATION,
              ImmutableList.of(),
              WsmIamRole.READER,
              ImmutableList.of(ControlledResourceIamRole.READER));

  public static ImmutableMap<WsmIamRole, ImmutableList<ControlledResourceIamRole>>
      USER_SHARED_MAPPING = SHARED_RESOURCE_MAPPING;

  public static ImmutableMap<WsmIamRole, ImmutableList<ControlledResourceIamRole>>
      APPLICATION_SHARED_MAPPING = SHARED_RESOURCE_MAPPING;

  public static ImmutableMap<WsmIamRole, ImmutableList<ControlledResourceIamRole>>
      USER_PRIVATE_MAPPING =
          ImmutableMap.of(
              WsmIamRole.OWNER,
              ImmutableList.of(
                  ControlledResourceIamRole.ASSIGNER, ControlledResourceIamRole.EDITOR),
              WsmIamRole.WRITER,
              ImmutableList.of(ControlledResourceIamRole.EDITOR),
              // Applications and readers have no permissions on private resources.
              WsmIamRole.APPLICATION,
              ImmutableList.of(),
              WsmIamRole.READER,
              ImmutableList.of());

  public static ImmutableMap<WsmIamRole, ImmutableList<ControlledResourceIamRole>>
      APPLICATION_PRIVATE_MAPPING =
          ImmutableMap.of(
              WsmIamRole.OWNER,
              ImmutableList.of(ControlledResourceIamRole.EDITOR),
              WsmIamRole.WRITER,
              ImmutableList.of(ControlledResourceIamRole.EDITOR),
              // Applications and readers have no permissions on private resources.
              WsmIamRole.APPLICATION,
              ImmutableList.of(),
              WsmIamRole.READER,
              ImmutableList.of());

  public static ImmutableMap<WsmIamRole, ImmutableList<ControlledResourceIamRole>>
      getInheritanceMapping(AccessScopeType accessScope, ManagedByType managedBy) {
    if (accessScope == AccessScopeType.ACCESS_SCOPE_SHARED) {
      if (managedBy == ManagedByType.MANAGED_BY_USER) {
        return USER_SHARED_MAPPING;
      } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
        return APPLICATION_SHARED_MAPPING;
      }
    } else if (accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      if (managedBy == ManagedByType.MANAGED_BY_USER) {
        return USER_PRIVATE_MAPPING;
      } else if (managedBy == ManagedByType.MANAGED_BY_APPLICATION) {
        return APPLICATION_PRIVATE_MAPPING;
      }
    }
    throw new InternalLogicException(
        String.format(
            "Inheritance map not specified for access scope %s and ManagedByType %s",
            accessScope.toString(), managedBy.toString()));
  }
}
