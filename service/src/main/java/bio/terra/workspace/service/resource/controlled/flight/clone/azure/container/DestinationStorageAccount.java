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
    @Nullable UUID wsmResourceId) {}
