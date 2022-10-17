package bio.terra.workspace.service.spendprofile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * A Terra resource modeling an account for spending money in the cloud.
 *
 * <p>Each Spend Profile may have cloud native billing resources associated with it. Spend Profiles
 * also have Terra IAM to manage who is allowed to modify and link them.
 */
public class SpendProfile {
  /** The unique identifier of the SpendProfile. */
  private final SpendProfileId id;

  /** The id of the Google Billing Account associated with the SpendProfile, if there is one. */
  private final Optional<String> billingAccountId;

  private final Optional<UUID> tenantId;

  private final Optional<UUID> subscriptionId;

  private final Optional<String> managedResourceGroupId;

  @JsonCreator
  public SpendProfile(
      @JsonProperty("id") SpendProfileId id,
      @JsonProperty("billingAccountId") @Nullable String billingAccountId,
      @JsonProperty("tenantId") @Nullable UUID tenantId,
      @JsonProperty("subscriptionId") @Nullable UUID subscriptionId,
      @JsonProperty("managedResourceGroupId") @Nullable String managedResourceGroupId) {
    this.id = id;
    this.billingAccountId = Optional.ofNullable(billingAccountId);
    this.tenantId = Optional.ofNullable(tenantId);
    this.subscriptionId = Optional.ofNullable(subscriptionId);
    this.managedResourceGroupId = Optional.ofNullable(managedResourceGroupId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SpendProfile that = (SpendProfile) o;
    return Objects.equals(id, that.id)
        && Objects.equals(billingAccountId, that.billingAccountId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(subscriptionId, that.subscriptionId)
        && Objects.equals(managedResourceGroupId, that.managedResourceGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, billingAccountId, tenantId, subscriptionId, managedResourceGroupId);
  }

  public static class Builder {
    private SpendProfileId id;
    private String billingAccountId;
    private UUID tenantId;
    private UUID subscriptionId;
    private String managedResourceGroupId;

    public Builder id(SpendProfileId spendProfileId) {
      this.id = spendProfileId;
      return this;
    }

    public Builder billingAccountId(String billingAccountId) {
      this.billingAccountId = billingAccountId;
      return this;
    }

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder subscriptionId(UUID subscriptionId) {
      this.subscriptionId = subscriptionId;
      return this;
    }

    public Builder managedResourceGroupId(String managedResourceGroupId) {
      this.managedResourceGroupId = managedResourceGroupId;
      return this;
    }

    public SpendProfile build() {
      return new SpendProfile(
          id, billingAccountId, tenantId, subscriptionId, managedResourceGroupId);
    }
  }

  public static SpendProfile.Builder builder() {
    return new SpendProfile.Builder();
  }

  public SpendProfileId getId() {
    return id;
  }

  public Optional<String> getBillingAccountId() {
    return billingAccountId;
  }

  public Optional<UUID> getTenantId() {
    return tenantId;
  }

  public Optional<UUID> getSubscriptionId() {
    return subscriptionId;
  }

  public Optional<String> getManagedResourceGroupId() {
    return managedResourceGroupId;
  }
}
