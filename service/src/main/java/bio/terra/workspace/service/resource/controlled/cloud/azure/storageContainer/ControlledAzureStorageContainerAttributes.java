package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import com.azure.resourcemanager.storage.models.PublicAccess;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureStorageContainerAttributes {
  private final String storageAccountName;
  private final String storageContainerName;
  private final PublicAccess publicAccess;

  @JsonCreator
  public ControlledAzureStorageContainerAttributes(
          @JsonProperty("storageAccountName") String storageAccountName,
          @JsonProperty("storageContainerName") String storageContainerName,
          @JsonProperty("publicAccess") PublicAccess publicAccess) {
    this.storageAccountName = storageAccountName;
    this.storageContainerName = storageContainerName;
    this.publicAccess = publicAccess;
  }

  public String getStorageAccountName() {
    return storageAccountName;
  }

  public String getStorageContainerName() {
    return storageContainerName;
  }

  public PublicAccess getPublicAccess() { return publicAccess; }

}
