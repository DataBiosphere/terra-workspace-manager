package bio.terra.workspace.service.resource;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public abstract class WsmResource {
  private String name;
  private String description;
  @Nullable private UUID resourceId;
  private UUID workspaceId;
  private boolean isVisible;
  private String associatedApp;
  private String owner;

  public WsmResource(
      String name,
      String description,
      UUID resourceId,
      UUID workspaceId,
      boolean isVisible,
      String associatedApp,
      String owner) {
    this.name = name;
    this.description = description;
    this.resourceId = resourceId;
    this.workspaceId = workspaceId;
    this.isVisible = isVisible;
    this.associatedApp = associatedApp;
    this.owner = owner;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setResourceId(UUID resourceId) {
    this.resourceId = resourceId;
  }

  public void setWorkspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
  }

  public String getAssociatedApp() {
    return associatedApp;
  }

  public void setVisible(boolean visible) {
    isVisible = visible;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Optional<UUID> getResourceId() {
    return Optional.ofNullable(resourceId);
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public void setAssociatedApp(String associatedApp) {
    this.associatedApp = associatedApp;
  }

  public boolean isVisible() {
    return isVisible;
  }

  public abstract StewardshipType getStewardshipType();

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }
}
