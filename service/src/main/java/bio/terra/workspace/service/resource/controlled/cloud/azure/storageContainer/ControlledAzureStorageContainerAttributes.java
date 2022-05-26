package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureStorageContainerAttributes {
  private final String storageAccountName;
  private final String storageContainerName;

  @JsonCreator
  public ControlledAzureStorageContainerAttributes(
          @JsonProperty("storageAccountName") String storageAccountName,
          @JsonProperty("storageContainerName") String storageContainerName) {
    this.storageAccountName = storageAccountName;
    this.storageContainerName = storageContainerName;
  }

  public String getStorageAccountName() {
    return storageAccountName;
  }

  public String getStorageContainerName() {
    return storageContainerName;
  }

}
