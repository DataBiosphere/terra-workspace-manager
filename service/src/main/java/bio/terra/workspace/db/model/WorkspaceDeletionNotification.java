package bio.terra.workspace.db.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceDeletionNotification(
    UUID workspaceId, String deletingUserId, OffsetDateTime deletedAt) {}
