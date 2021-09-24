package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

public class AzureCloudContext {
  /**
   * Format version for serialized form of Azure cloud context - deliberately chosen to be different
   * from the GCP cloud context version.
   */
  @VisibleForTesting public static final long AZURE_CLOUD_CONTEXT_DB_VERSION = 100;

  private final String azureTenantId;
  private final String azureSubscriptionId;
  private final String azureResourceGroupId;

  @JsonCreator
  public AzureCloudContext(
      @JsonProperty String azureTenantId,
      @JsonProperty String azureSubscriptionId,
      @JsonProperty String azureResourceGroupId) {
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

  public ApiAzureContext toApi() {
    return new ApiAzureContext()
        .tenantId(getAzureTenantId())
        .subscriptionId(getAzureSubscriptionId())
        .resourceGroupId(getAzureResourceGroupId());
  }

  public static AzureCloudContext fromApi(ApiAzureContext azureContext) {
    return new AzureCloudContext(
        azureContext.getTenantId(),
        azureContext.getSubscriptionId(),
        azureContext.getResourceGroupId());
  }

  // -- serdes for the GcpCloudContext --

  public String serialize() {
    AzureCloudContextV100 dbContext =
        AzureCloudContextV100.from(
            getAzureTenantId(), getAzureSubscriptionId(), getAzureResourceGroupId());
    return DbSerDes.toJson(dbContext);
  }

  public static AzureCloudContext deserialize(String json) {
    AzureCloudContextV100 result = DbSerDes.fromJson(json, AzureCloudContextV100.class);
    if (result.version != AZURE_CLOUD_CONTEXT_DB_VERSION) {
      throw new InvalidSerializedVersionException("Invalid serialized version");
    }
    return new AzureCloudContext(
        result.azureTenantId, result.azureSubscriptionId, result.azureResourceGroupId);
  }

  @VisibleForTesting
  public static class AzureCloudContextV100 {
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
