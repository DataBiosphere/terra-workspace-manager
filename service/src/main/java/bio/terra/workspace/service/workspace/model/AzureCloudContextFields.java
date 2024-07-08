package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

public class AzureCloudContextFields {
  private final String azureTenantId;
  private final String azureSubscriptionId;
  private final String azureResourceGroupId;

  @JsonCreator
  public AzureCloudContextFields(
      @JsonProperty("azureTenantId") String azureTenantId,
      @JsonProperty("azureSubscriptionId") String azureSubscriptionId,
      @JsonProperty("azureResourceGroupId") String azureResourceGroupId) {
    this.azureTenantId = azureTenantId;
    this.azureSubscriptionId = azureSubscriptionId;
    this.azureResourceGroupId = azureResourceGroupId;
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

  public void toApi(ApiAzureContext azureContext) {
    azureContext
        .tenantId(azureTenantId)
        .subscriptionId(azureSubscriptionId)
        .resourceGroupId(azureResourceGroupId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AzureCloudContextFields that)) return false;
    return Objects.equal(azureTenantId, that.azureTenantId)
        && Objects.equal(azureSubscriptionId, that.azureSubscriptionId)
        && Objects.equal(azureResourceGroupId, that.azureResourceGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(azureTenantId, azureSubscriptionId, azureResourceGroupId);
  }

  // -- serdes for the AzureCloudContext --

  public String serialize() {
    AzureCloudContextV100 dbContext =
        AzureCloudContextV100.from(azureTenantId, azureSubscriptionId, azureResourceGroupId);
    return DbSerDes.toJson(dbContext);
  }

  public static AzureCloudContextFields deserialize(String dbContext) {
    try {
      AzureCloudContextV100 result = DbSerDes.fromJson(dbContext, AzureCloudContextV100.class);
      if (result.version != AzureCloudContextV100.AZURE_CLOUD_CONTEXT_DB_VERSION) {
        throw new InvalidSerializedVersionException("Invalid serialized version");
      }
      return new AzureCloudContextFields(
          result.azureTenantId, result.azureSubscriptionId, result.azureResourceGroupId);

    } catch (SerializationException e) {
      throw new InvalidSerializedVersionException("Invalid serialized version", e);
    }
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
