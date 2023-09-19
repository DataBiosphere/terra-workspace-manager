package bio.terra.workspace.service.resource.controlled.cloud.azure;

import java.util.UUID;

record StorageAccountCoordinates(UUID workspaceUuid, UUID storageContainerUuid) {
}
