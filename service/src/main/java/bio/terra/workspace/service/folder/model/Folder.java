package bio.terra.workspace.service.folder.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;

public record Folder(
    UUID id,
    UUID workspaceId,
    String displayName,
    @Nullable String description,
    @Nullable UUID parentFolderId,
    Map<String, String> properties,
    String createdByEmail,
    // null when building a folder to create; Postgres will set createdDate.
    @Nullable OffsetDateTime createdDate) {

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Folder f = (Folder) o;
    return new EqualsBuilder()
        .append(id, f.id)
        .append(displayName, f.displayName)
        .append(parentFolderId, f.parentFolderId)
        .append(properties, f.properties)
        .append(createdByEmail, f.createdByEmail)
        .isEquals();
  }
}
