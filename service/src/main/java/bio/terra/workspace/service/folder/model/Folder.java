package bio.terra.workspace.service.folder.model;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

public record Folder(
    UUID id,
    UUID workspaceId,
    String displayName,
    @Nullable String description,
    @Nullable UUID parentFolderId,
    Map<String, String> properties) {}
