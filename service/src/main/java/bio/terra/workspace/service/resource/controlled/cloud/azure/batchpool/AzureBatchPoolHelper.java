package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

public class AzureBatchPoolHelper {
  public static final String SHARED_BATCH_ACCOUNT_FOUND_IN_LANDING_ZONE =
      "Shared batch account found in landing zone. Landing zone ID='%s'";
  public static final String SHARED_BATCH_ACCOUNT_NOT_FOUND_IN_LANDING_ZONE =
      "Shared batch account not found in landing zone. Landing zone ID='%s'.";
  public static final String SHARED_BATCH_ACCOUNT_FOUND_IN_AZURE =
      "Shared batch account found. TenantId='%s', SubscriptionId='%s', ResourceGroupId='%s'";
  public static final String SHARED_BATCH_ACCOUNT_NOT_FOUND_IN_AZURE =
      "Shared batch account not found. TenantId='%s', SubscriptionId='%s', ResourceGroupId='%s'";

  public static final String PET_UAMI_FOUND =
      "Assigning Identity to Batch Pool. UserAssignedManagedIdentity='%s'";
  public static final String BATCH_POOL_UAMI = "BatchPoolUAMI";
}
