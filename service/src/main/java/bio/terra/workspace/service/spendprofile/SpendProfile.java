package bio.terra.workspace.service.spendprofile;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.service.workspace.exceptions.NoAzureAppCoordinatesException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
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
    CloudPlatform cloudPlatform,
    Optional<String> billingAccountId,
    Optional<UUID> tenantId,
    Optional<UUID> subscriptionId,
    Optional<String> managedResourceGroupId) {

  public SpendProfile {
    if (cloudPlatform == CloudPlatform.GCP) {
      if (billingAccountId.isEmpty()) {
        throw NoBillingAccountException.forSpendProfile(id);
      }
    } else if (cloudPlatform == CloudPlatform.AZURE) {
      if (managedResourceGroupId().isEmpty()
          || subscriptionId().isEmpty()
          || tenantId().isEmpty()) {
        throw NoAzureAppCoordinatesException.forSpendProfile(id);
      }
    } else {
      throw new ValidationException("Invalid cloud platform for spend profile: " + cloudPlatform);
    }
  }

  public static SpendProfile buildGcpSpendProfile(SpendProfileId id, String billingAccountId) {
    return new SpendProfile(id, CloudPlatform.GCP, Optional.of(billingAccountId), null, null, null);
  }
}
