package bio.terra.workspace.service.controlledresource.model;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import java.util.UUID;

@AutoValue
public abstract class ControlledResourceMetadata {
  public abstract UUID workspaceId();

  public abstract UUID resourceId();

  public abstract Optional<String> associatedApp();

  public abstract boolean visible();

  public abstract Optional<String> owner();

  public abstract Optional<Object> attributes();

  public static ControlledResourceMetadata.Builder builder() {
    return new AutoValue_ControlledResourceMetadata.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract ControlledResourceMetadata.Builder workspaceId(UUID value);

    public abstract ControlledResourceMetadata.Builder resourceId(UUID value);

    public abstract ControlledResourceMetadata.Builder associatedApp(String value);

    public abstract ControlledResourceMetadata.Builder visible(boolean value);

    public abstract ControlledResourceMetadata.Builder owner(String value);

    public abstract ControlledResourceMetadata.Builder attributes(Object value);

    public abstract ControlledResourceMetadata build();
  }
}
