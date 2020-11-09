package bio.terra.workspace.common.model;

import bio.terra.workspace.service.spendprofile.SpendProfileId;
import com.google.auto.value.AutoValue;
import java.util.Optional;
import java.util.UUID;

/** Internal representation of a Workspace. */
@AutoValue
public abstract class Workspace {

  public abstract UUID workspaceId();

  public abstract Optional<SpendProfileId> spendProfileId();

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
