package bio.terra.workspace.service.resource;

import java.util.UUID;

public abstract class ResourceInput {
  private String name;
  private String description;
  private UUID workspaceId;
  private boolean isVisible;
  private String owner;

  public ResourceInput(
      String name,
      String description,
      UUID workspaceId,
      boolean isVisible,
      String owner) {
    this.name = name;
    this.description = description;
    this.workspaceId = workspaceId;
    this.isVisible = isVisible;
    this.owner = owner;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setWorkspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
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

  public UUID getWorkspaceId() {
    return workspaceId;
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
