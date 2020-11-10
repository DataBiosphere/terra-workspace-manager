package bio.terra.workspace.common.model;

import bio.terra.workspace.service.spendprofile.SpendProfileId;
import com.google.auto.value.AutoValue;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal representation of a Workspace.
 *
 * <p>A workspace is a collection of resources, data references, and applications with some shared
 * context. In general, a workspace is the fundamental unit of analysis in Terra. Workspaces may
 * have an associated billing account and may have zero or one associated GCP projects.
 */
@AutoValue
public abstract class Workspace {

  /** The globally unique identifier of this workspace */
  public abstract UUID workspaceId();

  /**
   * The spend profile ID associated with this project, if one exists.
   *
   * <p>In the future, this will correlate to a spend profile in the Spend Profile Manager. For now,
   * it's just a unique identifier. To associate a GCP project with a workspace, the workspace must
   * have a spend profile. They are not needed otherwise.
   */
  public abstract Optional<SpendProfileId> spendProfileId();

  /** Temporary feature flag indicating whether this workspace uses MC Terra features. */
  public abstract WorkspaceStage workspaceStage();

  public static Builder builder() {
    return new AutoValue_Workspace.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder workspaceId(UUID value);

    public abstract Builder spendProfileId(Optional<SpendProfileId> value);

    public abstract Builder workspaceStage(WorkspaceStage value);

    public abstract Workspace build();
  }
}
