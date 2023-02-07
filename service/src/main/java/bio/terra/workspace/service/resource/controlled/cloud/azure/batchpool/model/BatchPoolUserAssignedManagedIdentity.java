package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.model;

import java.util.UUID;

public record BatchPoolUserAssignedManagedIdentity(String resourceGroupName, String name, UUID clientId) {
    public BatchPoolUserAssignedManagedIdentity(String resourceGroupName, String name, UUID clientId) {
        this.resourceGroupName = resourceGroupName;
        this.name = name;
        this.clientId = clientId;
    }
    public BatchPoolUserAssignedManagedIdentity(String resourceGroupName, String name) {
        this(resourceGroupName, name, null);
    }
    public BatchPoolUserAssignedManagedIdentity(String resourceGroupName, UUID clientId) {
        this(resourceGroupName, null, clientId);
    }
}
