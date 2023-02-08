package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.model;

import java.util.UUID;

public record BatchPoolUserAssignedManagedIdentity(
    String resourceGroupName, String name, UUID clientId) {}
