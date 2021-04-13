package bio.terra.workspace.service.resource.controlled.mappings;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Static mappings of the resource-level IAM roles inherited by workspace-level IAM roles.
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
 * <p>This mapping is implemented in Sam using hierarchical resources: workspaces are "parent"
 * objects, and controlled resources are "child" objects which inherit permissions from the
 * workspace. As part of this, Sam also has a similar mapping configured for granting permissions.
 * If you change this map, also check the definitions of the reader, writer, and editor roles on
 * 'workspace' resources in that mapping:
 * https://github.com/broadinstitute/sam/blob/develop/src/main/resources/reference.conf
 */
public class ControlledResourceInheritanceMapping {

  private ControlledResourceInheritanceMapping() {}

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

  public static final Multimap<WsmIamRole, ControlledResourceIamRole> USER_SHARED_MAPPING =
      SHARED_RESOURCE_MAPPING;

  public static final Multimap<WsmIamRole, ControlledResourceIamRole> APPLICATION_SHARED_MAPPING =
      SHARED_RESOURCE_MAPPING;

  // Applications and readers have no permissions on private resources, and MultiMaps ignore keys
  // without values, so we do not add them here.
  public static final Multimap<WsmIamRole, ControlledResourceIamRole> USER_PRIVATE_MAPPING =
      new ImmutableMultimap.Builder<WsmIamRole, ControlledResourceIamRole>()
          .putAll(
              WsmIamRole.OWNER,
              ControlledResourceIamRole.ASSIGNER,
              ControlledResourceIamRole.EDITOR)
          .putAll(WsmIamRole.WRITER, ControlledResourceIamRole.EDITOR)
          .build();

  public static final Multimap<WsmIamRole, ControlledResourceIamRole> APPLICATION_PRIVATE_MAPPING =
      new ImmutableMultimap.Builder<WsmIamRole, ControlledResourceIamRole>()
          .putAll(WsmIamRole.OWNER, ControlledResourceIamRole.EDITOR)
          .putAll(WsmIamRole.WRITER, ControlledResourceIamRole.EDITOR)
          .build();
}
