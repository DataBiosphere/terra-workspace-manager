package bio.terra.workspace.service.resource.controlled;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledResourceDbModel {
  private final UUID workspaceId;
  private final UUID resourceId;
  @Nullable private final String associatedApp;
  private final boolean isVisible;

  @Nullable private final String owner;
  private final String attributes;

  // use builder instead
  private ControlledResourceDbModel(
      UUID workspaceId,
      UUID resourceId,
      @Nullable String associatedApp,
      boolean isVisible,
      @Nullable String owner,
      String attributes) {
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
    this.associatedApp = associatedApp;
    this.isVisible = isVisible;
    this.owner = owner;
    this.attributes = attributes;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public Optional<String> getAssociatedApp() {
    return Optional.ofNullable(associatedApp);
  }

  public boolean isVisible() {
    return isVisible;
  }

  public Optional<String> getOwner() {
    return Optional.ofNullable(owner);
  }

  public String getAttributes() {
    return attributes;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ControlledResourceDbModel)) {
      return false;
    }
    ControlledResourceDbModel metadata = (ControlledResourceDbModel) o;
    return isVisible() == metadata.isVisible()
        && Objects.equals(getWorkspaceId(), metadata.getWorkspaceId())
        && Objects.equals(getResourceId(), metadata.getResourceId())
        && Objects.equals(getAssociatedApp(), metadata.getAssociatedApp())
        && Objects.equals(getOwner(), metadata.getOwner())
        && Objects.equals(getAttributes(), metadata.getAttributes());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getWorkspaceId(),
        getResourceId(),
        getAssociatedApp(),
        isVisible(),
        getOwner(),
        getAttributes());
  }

  public static class Builder {

    private UUID workspaceId;
    private UUID resourceId;
    private String associatedApp;
    private boolean isVisible;
    private String owner;
    private String attributes;

    public Builder setWorkspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public Builder setResourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder setAssociatedApp(String associatedApp) {
      this.associatedApp = associatedApp;
      return this;
    }

    public Builder setIsVisible(boolean isVisible) {
      this.isVisible = isVisible;
      return this;
    }

    public Builder setOwner(String owner) {
      this.owner = owner;
      return this;
    }

    public Builder setAttributes(String attributes) {
      this.attributes = attributes;
      return this;
    }

    public ControlledResourceDbModel build() {
      return new ControlledResourceDbModel(
          workspaceId, resourceId, associatedApp, isVisible, owner, attributes);
    }
  }
}
