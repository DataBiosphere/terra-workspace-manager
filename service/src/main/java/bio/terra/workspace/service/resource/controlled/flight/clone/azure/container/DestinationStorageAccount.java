package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Represents a destination storage account for the purposes of cloning.
 *
 * @param storageAccountType Type of storage account, i.e., landing zone or workspace
 * @param lzResourceId If landing zone, the Azure ID of the landing zone storage account
 * @param wsmResourceId If workspace, the WSM resource ID of the workspace storage account
 */
public record DestinationStorageAccount(
    StorageAccountType storageAccountType,
    @Nullable String lzResourceId,
    @Nullable UUID wsmResourceId) {
  public DestinationStorageAccount(
      StorageAccountType storageAccountType, String lzResourceId, UUID wsmResourceId) {
    if (lzResourceId != null && wsmResourceId != null) {
      throw new IllegalArgumentException(
          "Destination storage account may not have both a landing zone resource ID and WSM resource ID");
    }

    if (lzResourceId == null && wsmResourceId == null) {
      throw new IllegalArgumentException(
          "Destination storage account requires landing zore resource ID or WSM resource ID");
    }

    this.storageAccountType = storageAccountType;
    this.lzResourceId = lzResourceId;
    this.wsmResourceId = wsmResourceId;
  }
}
