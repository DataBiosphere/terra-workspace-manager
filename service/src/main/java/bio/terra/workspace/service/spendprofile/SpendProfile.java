package bio.terra.workspace.service.spendprofile;

import java.util.Optional;
import java.util.UUID;

/**
 * A Terra resource modeling an account for spending money in the cloud.
 *
 * <p>Each Spend Profile may have cloud native billing resources associated with it. Spend Profiles
 * also have Terra IAM to manage who is allowed to modify and link them.
 */
public record SpendProfile(
    SpendProfileId id,
    Optional<String> billingAccountId,
    Optional<UUID> tenantId,
    Optional<UUID> subscriptionId,
    Optional<String> managedResourceGroupId) {

  public static SpendProfile buildGcpSpendProfile(SpendProfileId id, String billingAccountId) {
    return new SpendProfile(id, Optional.of(billingAccountId), null, null, null);
  }

  public static SpendProfile buildEmptyProfile(SpendProfileId id) {
    return new SpendProfile(id, null, null, null, null);
  }
}
