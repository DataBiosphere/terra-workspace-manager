package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.generated.model.ApiResourceLineageEntry;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * A single resource lineage entry.
 *
 * <p>In resource table, the resource_lineage column contains a jsonb list of ResourceLineageEntry.
 *
 * <p>When you have a jsonb list of Foo, you must deserialize into Foo[].class. You can't create a
 * FooList class that contains List<Foo> and deserialize into FooList. So there is no
 * ResourceLineage.java. See https://stackoverflow.com/a/25512128/6447189.
 */
public class ResourceLineageEntry {
  private final UUID sourceWorkspaceId;
  private final UUID sourceResourceId;

  @JsonCreator
  public ResourceLineageEntry(
      @JsonProperty("sourceWorkspaceId") UUID sourceWorkspaceId,
      @JsonProperty("sourceResourceId") UUID sourceResourceId) {
    this.sourceWorkspaceId = sourceWorkspaceId;
    this.sourceResourceId = sourceResourceId;
  }

  public UUID getSourceWorkspaceId() {
    return sourceWorkspaceId;
  }

  public UUID getSourceResourceId() {
    return sourceResourceId;
  }

  public ApiResourceLineageEntry toApiModel() {
    return new ApiResourceLineageEntry()
        .sourceWorkspaceId(sourceWorkspaceId)
        .sourceResourceId(sourceResourceId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ResourceLineageEntry)) {
      return false;
    }
    ResourceLineageEntry entry = (ResourceLineageEntry) o;
    return sourceWorkspaceId.equals(entry.sourceWorkspaceId)
        && sourceResourceId.equals(entry.sourceResourceId);
  }
}
