package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class AzureCloudContext {
  private String azureTenantId;
  private String azureSubscriptionId;
  private String azureResourceGroupId;

  // Constructor for Jackson
  public AzureCloudContext() {}

  // Constructor for deserializer
  public AzureCloudContext(
      String azureTenantId, String azureSubscriptionId, String azureResourceGroupId) {
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

  public void setAzureTenantId(String azureTenantId) {
    this.azureTenantId = azureTenantId;
  }

  public void setAzureSubscriptionId(String azureSubscriptionId) {
    this.azureSubscriptionId = azureSubscriptionId;
  }

  public void setAzureResourceGroupId(String azureResourceGroupId) {
    this.azureResourceGroupId = azureResourceGroupId;
  }

  public ApiAzureContext toApi() {
    return new ApiAzureContext()
        .tenantId(azureTenantId)
        .subscriptionId(azureSubscriptionId)
        .resourceGroupId(azureResourceGroupId);
  }

  public static AzureCloudContext fromApi(ApiAzureContext azureContext) {
    return new AzureCloudContext(
        azureContext.getTenantId(),
        azureContext.getSubscriptionId(),
        azureContext.getResourceGroupId());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AzureCloudContext that = (AzureCloudContext) o;

    return new EqualsBuilder()
        .append(azureTenantId, that.azureTenantId)
        .append(azureSubscriptionId, that.azureSubscriptionId)
        .append(azureResourceGroupId, that.azureResourceGroupId)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(azureTenantId)
        .append(azureSubscriptionId)
        .append(azureResourceGroupId)
        .toHashCode();
  }

  // -- serdes for the AzureCloudContext --

  public String serialize() {
    AzureCloudContextV100 dbContext =
        AzureCloudContextV100.from(azureTenantId, azureSubscriptionId, azureResourceGroupId);
    return DbSerDes.toJson(dbContext);
  }

  public static AzureCloudContext deserialize(String json) {
    AzureCloudContextV100 result = DbSerDes.fromJson(json, AzureCloudContextV100.class);
    if (result.version != AzureCloudContextV100.AZURE_CLOUD_CONTEXT_DB_VERSION) {
      throw new InvalidSerializedVersionException("Invalid serialized version");
    }
    return new AzureCloudContext(
        result.azureTenantId, result.azureSubscriptionId, result.azureResourceGroupId);
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
