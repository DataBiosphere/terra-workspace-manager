package bio.terra.workspace.common.model;

import com.google.auto.value.AutoValue;
import java.util.UUID;
import javax.annotation.Nullable;

/** Internal representation of a Workspace. */
@AutoValue
public abstract class Workspace {

  public abstract UUID workspaceId();

  @Nullable
  public abstract String spendProfileId();

  public abstract WorkspaceStage workspaceStage();

  public static Builder builder() {
    return new AutoValue_Workspace.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder workspaceId(UUID value);

    public abstract Builder spendProfileId(String value);

    public abstract Builder workspaceStage(WorkspaceStage value);

    public abstract Workspace build();
  }
}
