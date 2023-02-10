package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Each controlled resource category has a different pattern of what role from what object
 * (workspace or resource) is sync'd to what role on the cloud platform resource.
 *
 * <p>This class provides the recipe for how to map some source role (either workspace or resource)
 * into a target role.
 *
 * <p>NOTE: when setting temporary grants, the ControlledResourceService uses the
 * ControlledResourceIamRole.EDITOR in all cases. If that changes for some reason, reflected in this
 * module, we will need to make a compensating change in ControlledResourceService.
 */
public class ControlledResourceSyncMapping {

  public static final List<SyncMapping> USER_SHARED_MAPPING =
      ImmutableList.of(
          new SyncMapping(
              RoleSource.WORKSPACE, WsmIamRole.OWNER, null, ControlledResourceIamRole.EDITOR),
          new SyncMapping(
              RoleSource.WORKSPACE, WsmIamRole.WRITER, null, ControlledResourceIamRole.EDITOR),
          new SyncMapping(
              RoleSource.WORKSPACE, WsmIamRole.READER, null, ControlledResourceIamRole.READER));

  public static final List<SyncMapping> USER_PRIVATE_MAPPING =
      ImmutableList.of(
          new SyncMapping(
              RoleSource.RESOURCE,
              null,
              ControlledResourceIamRole.EDITOR,
              ControlledResourceIamRole.EDITOR),
          new SyncMapping(
              RoleSource.RESOURCE,
              null,
              ControlledResourceIamRole.WRITER,
              ControlledResourceIamRole.WRITER));
  public static final List<SyncMapping> APPLICATION_SHARED_MAPPING =
      ImmutableList.of(
          new SyncMapping(
              RoleSource.RESOURCE,
              null,
              ControlledResourceIamRole.EDITOR,
              ControlledResourceIamRole.EDITOR),
          new SyncMapping(
              RoleSource.WORKSPACE, WsmIamRole.OWNER, null, ControlledResourceIamRole.WRITER),
          new SyncMapping(
              RoleSource.WORKSPACE, WsmIamRole.WRITER, null, ControlledResourceIamRole.WRITER),
          new SyncMapping(
              RoleSource.WORKSPACE, WsmIamRole.READER, null, ControlledResourceIamRole.READER));
  public static final List<SyncMapping> APPLICATION_PRIVATE_MAPPING =
      ImmutableList.of(
          new SyncMapping(
              RoleSource.RESOURCE,
              null,
              ControlledResourceIamRole.EDITOR,
              ControlledResourceIamRole.EDITOR),
          new SyncMapping(
              RoleSource.RESOURCE,
              null,
              ControlledResourceIamRole.WRITER,
              ControlledResourceIamRole.WRITER),
          new SyncMapping(
              RoleSource.RESOURCE,
              null,
              ControlledResourceIamRole.READER,
              ControlledResourceIamRole.READER));

  private ControlledResourceSyncMapping() {}

  public enum RoleSource {
    WORKSPACE,
    RESOURCE
  }

  public static class SyncMapping {
    private final RoleSource roleSource;
    private final WsmIamRole workspaceRole; // if roleSource is WORKSPACE
    private final ControlledResourceIamRole resourceRole; // if roleSource is RESOURCE
    private final ControlledResourceIamRole targetRole;

    public SyncMapping(
        RoleSource roleSource,
        @Nullable WsmIamRole workspaceRole,
        @Nullable ControlledResourceIamRole resourceRole,
        ControlledResourceIamRole targetRole) {
      this.roleSource = roleSource;
      this.workspaceRole = workspaceRole;
      this.resourceRole = resourceRole;
      this.targetRole = targetRole;
    }

    public RoleSource getRoleSource() {
      return roleSource;
    }

    public Optional<WsmIamRole> getWorkspaceRole() {
      return Optional.ofNullable(workspaceRole);
    }

    public Optional<ControlledResourceIamRole> getResourceRole() {
      return Optional.ofNullable(resourceRole);
    }

    public ControlledResourceIamRole getTargetRole() {
      return targetRole;
    }
  }
}
