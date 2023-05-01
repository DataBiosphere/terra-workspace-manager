package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.Nullable;

public class AzureCloudContext implements CloudContext {
  private final String azureTenantId;
  private final String azureSubscriptionId;
  private final String azureResourceGroupId;
  private final @Nullable CloudContextCommonFields commonFields;

  @JsonCreator
  public AzureCloudContext(
    @JsonProperty("azureTenantId") String azureTenantId,
    @JsonProperty("azureSubscriptionId") String azureSubscriptionId,
    @JsonProperty("azureResourceGroupId") String azureResourceGroupId,
    @JsonProperty("commonFields") @Nullable CloudContextCommonFields commonFields) {
    this.azureTenantId = azureTenantId;
    this.azureSubscriptionId = azureSubscriptionId;
    this.azureResourceGroupId = azureResourceGroupId;
    this.commonFields = commonFields;
  }

  public String getAzureTenantId() {
    return azureTenantId;
  }

  public String getAzureSubscriptionId() {
    return azureSubscriptionId;
  }

  public String getAzureResourceGroupId() {
    return azureResourceGroupId;
  }

  @Override
  public CloudPlatform getCloudPlatform() {
    return CloudPlatform.AZURE;
  }

  @Override
  public @Nullable CloudContextCommonFields getCommonFields() {
    return commonFields;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(CloudPlatform cloudPlatform) {
    if (cloudPlatform != getCloudPlatform()) {
      throw new InternalLogicException(String
        .format("Invalid cast from %s to %s", getCloudPlatform(), cloudPlatform));
    }
    return (T) this;
  }

  public ApiAzureContext toApi() {
    return new ApiAzureContext()
        .tenantId(azureTenantId)
        .subscriptionId(azureSubscriptionId)
        .resourceGroupId(azureResourceGroupId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AzureCloudContext that)) return false;
    return Objects.equal(azureTenantId, that.azureTenantId) && Objects.equal(azureSubscriptionId, that.azureSubscriptionId) && Objects.equal(azureResourceGroupId, that.azureResourceGroupId) && Objects.equal(commonFields, that.commonFields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(azureTenantId, azureSubscriptionId, azureResourceGroupId, commonFields);
  }

// -- serdes for the AzureCloudContext --

  @Override
  public String serialize() {
    AzureCloudContextV100 dbContext =
        AzureCloudContextV100.from(azureTenantId, azureSubscriptionId, azureResourceGroupId);
    return DbSerDes.toJson(dbContext);
  }

  public static AzureCloudContext deserialize(DbCloudContext dbCloudContext) {
    AzureCloudContextV100 result = DbSerDes.fromJson(dbCloudContext.getContextJson(), AzureCloudContextV100.class);
    if (result.version != AzureCloudContextV100.AZURE_CLOUD_CONTEXT_DB_VERSION) {
      throw new InvalidSerializedVersionException("Invalid serialized version");
    }
    return new AzureCloudContext(
      result.azureTenantId,
      result.azureSubscriptionId,
      result.azureResourceGroupId,
      new CloudContextCommonFields(
        dbCloudContext.getSpendProfile(),
        dbCloudContext.getState(),
        dbCloudContext.getFlightId(),
        dbCloudContext.getError()));
  }

  @VisibleForTesting
  public static class AzureCloudContextV100 {
    /**
     * Format version for serialized form of Azure cloud context - deliberately chosen to be
     * different from the GCP cloud context version.
     */
    public static final long AZURE_CLOUD_CONTEXT_DB_VERSION = 100;

    /** Version marker to store in the db so that we can update the format later if we need to. */
    @JsonProperty public final long version = AZURE_CLOUD_CONTEXT_DB_VERSION;

    @JsonProperty public String azureTenantId;
    @JsonProperty public String azureSubscriptionId;
    @JsonProperty public String azureResourceGroupId;

    public static AzureCloudContextV100 from(
        String tenantId, String subscriptionId, String resourceGroupId) {
      AzureCloudContextV100 result = new AzureCloudContextV100();
      result.azureTenantId = tenantId;
      result.azureSubscriptionId = subscriptionId;
      result.azureResourceGroupId = resourceGroupId;
      return result;
    }
  }
}
