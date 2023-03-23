package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class ControlledAzureStorageContainerAttributes {
  private final String storageContainerName;

  @JsonCreator
  public ControlledAzureStorageContainerAttributes(
      @JsonProperty("storageContainerName") String storageContainerName) {
    this.storageContainerName = storageContainerName;
  }

  public String getStorageContainerName() {
    return storageContainerName;
  }
}
