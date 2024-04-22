package bio.terra.workspace.service.spendprofile.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.service.workspace.exceptions.NoAzureAppCoordinatesException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.google.common.base.Strings;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A Terra resource modeling an account for spending money in the cloud.
 *
 * <p>Each Spend Profile may have cloud native billing resources associated with it. Spend Profiles
 * also have Terra IAM to manage who is allowed to modify and link them.
 */
public record SpendProfile(
    SpendProfileId id,
    CloudPlatform cloudPlatform,
    @Nullable String billingAccountId,
    @Nullable UUID tenantId,
    @Nullable UUID subscriptionId,
    @Nullable String managedResourceGroupId,
    @Nullable SpendProfileOrganization organization) {

  public SpendProfile {
    if (cloudPlatform == CloudPlatform.GCP) {
      if (Strings.isNullOrEmpty(billingAccountId)) {
        throw NoBillingAccountException.forSpendProfile(id);
      }
    } else if (cloudPlatform == CloudPlatform.AZURE) {
      if (tenantId == null
          || subscriptionId == null
          || Strings.isNullOrEmpty(managedResourceGroupId)) {
        throw NoAzureAppCoordinatesException.forSpendProfile(id);
      }
    } else {
      throw new ValidationException("Invalid cloud platform for spend profile: " + cloudPlatform);
    }
  }

  public static SpendProfile buildGcpSpendProfile(SpendProfileId id, String billingAccountId) {
    return new SpendProfile(id, CloudPlatform.GCP, billingAccountId, null, null, null, null);
  }
}
