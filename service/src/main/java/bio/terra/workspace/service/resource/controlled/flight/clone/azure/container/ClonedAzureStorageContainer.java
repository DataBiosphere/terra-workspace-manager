package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.UUID;

public record ClonedAzureStorageContainer(
    CloningInstructions effectiveCloningInstructions,
    UUID sourceWorkspaceId,
    UUID sourceResourceId,
    ControlledAzureStorageContainerResource storageContainer)
    implements ClonedAzureResource {}
