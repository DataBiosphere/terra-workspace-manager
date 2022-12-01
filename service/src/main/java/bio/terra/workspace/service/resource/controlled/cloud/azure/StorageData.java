package bio.terra.workspace.service.resource.controlled.cloud.azure;

import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;

public record StorageData(
    String storageAccountName,
    String endpoint,
    ControlledAzureStorageContainerResource storageContainerResource) {}
