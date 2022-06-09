package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class ControlledAzureStorageContainerAttributes {
  private final UUID storageAccountId;
  private final String storageContainerName;

  @JsonCreator
  public ControlledAzureStorageContainerAttributes(
      @JsonProperty("storageAccountId") UUID storageAccountId,
      @JsonProperty("storageContainerName") String storageContainerName) {
    this.storageAccountId = storageAccountId;
    this.storageContainerName = storageContainerName;
  }

  public UUID getStorageAccountId() {
    return storageAccountId;
  }

  public String getStorageContainerName() {
    return storageContainerName;
  }
}
