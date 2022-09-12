package bio.terra.workspace.service.folder.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Optional;
import java.util.UUID;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableFolder.class)
public interface Folder extends WithFolder {

  UUID getId();

  UUID getWorkspaceId();

  String getDisplayName();

  Optional<String> getDescription();

  Optional<UUID> getParentFolderId();

  class Builder extends ImmutableFolder.Builder {}
}
