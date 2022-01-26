package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureStorageAttributes {
  private final String storageAccountName;
  private final String region;

  @JsonCreator
  public ControlledAzureStorageAttributes(
      @JsonProperty("storageAccountName") String storageAccountName,
      @JsonProperty("region") String region) {
    this.storageAccountName = storageAccountName;
    this.region = region;
  }

  public String getStorageAccountName() {
    return storageAccountName;
  }

  public String getRegion() {
    return region;
  }
}
