package bio.terra.workspace.service.resource;

import bio.terra.workspace.service.datareference.model.CloningInstructions;
import java.util.UUID;

public abstract class WsmResource {
  private final String name;
  private final CloningInstructions cloningInstructions;
  private final String description;
  private final UUID workspaceId;
  private final String owner;

  public WsmResource(
      String name,
      CloningInstructions cloningInstructions,
      String description,
      UUID workspaceId,
      String owner) {
    this.name = name;
    this.cloningInstructions = cloningInstructions;
    this.description = description;
    this.workspaceId = workspaceId;
    this.owner = owner;
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

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  public abstract StewardshipType getStewardshipType();

  public String getOwner() {
    return owner;
  }
}
