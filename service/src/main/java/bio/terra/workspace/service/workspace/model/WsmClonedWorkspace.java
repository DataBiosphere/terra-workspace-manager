package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class WsmClonedWorkspace {
  private UUID sourceWorkspaceId;
  private UUID destinationWorkspaceId;
  private List<WsmResourceCloneDetails> resources;

  public WsmClonedWorkspace() {}

  public UUID getSourceWorkspaceId() {
    return sourceWorkspaceId;
  }

  public void setSourceWorkspaceId(UUID sourceWorkspaceId) {
    this.sourceWorkspaceId = sourceWorkspaceId;
  }

  public UUID getDestinationWorkspaceId() {
    return destinationWorkspaceId;
  }

  public void setDestinationWorkspaceId(UUID destinationWorkspaceId) {
    this.destinationWorkspaceId = destinationWorkspaceId;
  }

  public List<WsmResourceCloneDetails> getResources() {
    return resources;
  }

  public void setResources(List<WsmResourceCloneDetails> resources) {
    this.resources = resources;
  }

  public ApiClonedWorkspace toApiModel() {
    return new ApiClonedWorkspace()
        .sourceWorkspaceId(sourceWorkspaceId)
        .destinationWorkspaceId(destinationWorkspaceId)
        .resources(
            resources.stream()
                .map(WsmResourceCloneDetails::toApiModel)
                .collect(Collectors.toList()));
  }
}
