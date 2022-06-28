package bio.terra.workspace.service.resource.referenced.terra.workspace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class ReferencedTerraWorkspaceAttributes {
  private final UUID referencedWorkspaceId;

  @JsonCreator
  public ReferencedTerraWorkspaceAttributes(
      @JsonProperty("referencedWorkspaceId") UUID referencedWorkspaceId) {
    this.referencedWorkspaceId = referencedWorkspaceId;
  }

  public UUID getReferencedWorkspaceId() {
    return referencedWorkspaceId;
  }
}
