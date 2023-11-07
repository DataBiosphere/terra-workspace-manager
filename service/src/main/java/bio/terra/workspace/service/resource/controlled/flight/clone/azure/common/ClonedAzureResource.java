package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import java.util.UUID;

public record ClonedAzureResource(
    CloningInstructions effectiveCloningInstructions,
    UUID sourceWorkspaceId,
    UUID sourceResourceId,
    ControlledResource resource) {
  public ClonedAzureResource(
      CloningInstructions effectiveCloningInstructions,
      UUID sourceWorkspaceId,
      UUID sourceResourceId) {
    this(effectiveCloningInstructions, sourceWorkspaceId, sourceResourceId, null);
  }

  public ControlledAzureStorageContainerResource storageContainer() {
    return this.resource.castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_CONTAINER);
  }

  public ControlledAzureManagedIdentityResource managedIdentity() {
    return this.resource.castByEnum(WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);
  }
}
