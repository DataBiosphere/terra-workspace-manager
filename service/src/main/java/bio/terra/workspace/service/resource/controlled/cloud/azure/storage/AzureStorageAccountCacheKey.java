package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

public record AzureStorageAccountCacheKey(
    String subscriptionId, String managedResourceGroupId, String storageAccountName) {}
