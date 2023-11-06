package bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity;

import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.ClonedAzureResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.UUID;

public record ClonedAzureManagedIdentity(
    CloningInstructions effectiveCloningInstructions,
    UUID sourceWorkspaceId,
    UUID sourceResourceId,
    ControlledAzureManagedIdentityResource managedIdentity) implements ClonedAzureResource<ControlledAzureManagedIdentityResource> {}
