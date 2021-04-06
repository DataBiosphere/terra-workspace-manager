package bio.terra.workspace.service.resource.controlled.mappings;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Static mappings of workspace-level IAM roles to resource-level IAM roles.
 *
 * <p>This mapping represents which resource-level roles are granted to workspace members based on
 * their workspace-level role. This mapping is slightly different for each category of controlled
 * resource (user-shared, user-private, app-shared, app-private), so this class holds four separate
 * maps.
 *
 * <p>For example, all workspace readers should be readers of a user-shared bucket in that
 * workspace, but the same is not true for a user-private bucket. This is represented in the maps
 * below, where WsmIamRole.READER maps to ControlledResourceIamRole.READER for shared resources but
 * that entry is not present for private resources.
 *
 * <p>This maps workspace roles to the resource roles, which are used for granting cloud
 * permissions. Sam also has a similar mapping configured for granting Sam permissions. If you
 * change this map, also check that mapping:
 * https://github.com/broadinstitute/sam/blob/develop/src/main/resources/reference.conf
 */
public class ControlledResourceInheritanceMapping {
  // Currently, shared resources have the same permission inheritance regardless of whether they
  // are user- or application-controlled. This list is pulled out separately as a convenience.
  private static final Multimap<WsmIamRole, ControlledResourceIamRole> SHARED_RESOURCE_MAPPING =
      new ImmutableMultimap.Builder<WsmIamRole, ControlledResourceIamRole>()
          .putAll(
              WsmIamRole.OWNER, ControlledResourceIamRole.EDITOR, ControlledResourceIamRole.WRITER)
          .putAll(
              WsmIamRole.WRITER, ControlledResourceIamRole.EDITOR, ControlledResourceIamRole.WRITER)
          .putAll(WsmIamRole.READER, ControlledResourceIamRole.READER)
          .build();

  private static final Multimap<WsmIamRole, ControlledResourceIamRole> USER_SHARED_MAPPING =
      SHARED_RESOURCE_MAPPING;

  private static final Multimap<WsmIamRole, ControlledResourceIamRole> APPLICATION_SHARED_MAPPING =
      SHARED_RESOURCE_MAPPING;

  // Applications and readers have no permissions on private resources, and MultiMaps ignore keys
  // without values, so we do not add them here.
  private static final Multimap<WsmIamRole, ControlledResourceIamRole> USER_PRIVATE_MAPPING =
      new ImmutableMultimap.Builder<WsmIamRole, ControlledResourceIamRole>()
          .putAll(
              WsmIamRole.OWNER,
              ControlledResourceIamRole.ASSIGNER,
              ControlledResourceIamRole.EDITOR)
          .putAll(WsmIamRole.WRITER, ControlledResourceIamRole.EDITOR)
          .build();

  private static final Multimap<WsmIamRole, ControlledResourceIamRole> APPLICATION_PRIVATE_MAPPING =
      new ImmutableMultimap.Builder<WsmIamRole, ControlledResourceIamRole>()
          .putAll(WsmIamRole.OWNER, ControlledResourceIamRole.EDITOR)
          .putAll(WsmIamRole.WRITER, ControlledResourceIamRole.EDITOR)
          .build();

  /**
   * Returns the resource-level roles that each workspace-level role inherits on the specified
   * category of resource (user-shared, user-private, app-shared, or app-private).
   *
   * @return A map whose keys are workspace-level roles and values are the corresponding inherited
   *     resource-level roles for the specified resource category.
   */
  public static Multimap<WsmIamRole, ControlledResourceIamRole> getInheritanceMapping(
      AccessScopeType accessScope, ManagedByType managedBy) {
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
            "Inheritance map not specified for AccessScopeType %s and ManagedByType %s",
            accessScope.toString(), managedBy.toString()));
  }
}
