package bio.terra.workspace.service.resource.controlled.cloud.azure;

import java.util.UUID;

public record StorageAccountCoordinates(UUID workspaceUuid, UUID storageContainerUuid) {}
