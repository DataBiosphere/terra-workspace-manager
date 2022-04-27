package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiClonedWorkspace;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class WsmClonedWorkspace {
  private UUID sourceWorkspaceUuid;
  private UUID destinationWorkspaceUuid;
  private List<WsmResourceCloneDetails> resources;

  public WsmClonedWorkspace() {}

  public UUID getSourceWorkspaceUuid() {
    return sourceWorkspaceUuid;
  }

  public void setSourceWorkspaceUuid(UUID sourceWorkspaceUuid) {
    this.sourceWorkspaceUuid = sourceWorkspaceUuid;
  }

  public UUID getDestinationWorkspaceUuid() {
    return destinationWorkspaceUuid;
  }

  public void setDestinationWorkspaceUuid(UUID destinationWorkspaceUuid) {
    this.destinationWorkspaceUuid = destinationWorkspaceUuid;
  }

  public List<WsmResourceCloneDetails> getResources() {
    return resources;
  }

  public void setResources(List<WsmResourceCloneDetails> resources) {
    this.resources = resources;
  }

  public ApiClonedWorkspace toApiModel() {
    return new ApiClonedWorkspace()
        .sourceWorkspaceUuid(sourceWorkspaceUuid)
        .destinationWorkspaceUuid(destinationWorkspaceUuid)
        .resources(
            resources.stream()
                .map(WsmResourceCloneDetails::toApiModel)
                .collect(Collectors.toList()));
  }
}
