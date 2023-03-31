package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.resourcemanager.data;

import bio.terra.cloudres.common.CloudOperation;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.StorageManagerOperation;
import com.google.auto.value.AutoValue;
import com.google.gson.JsonObject;

@AutoValue
public abstract class CreateStorageContainerRequestData extends BaseStorageContainerRequestData {

  @Override
  public CloudOperation cloudOperation() {
    return StorageManagerOperation.AZURE_CREATE_STORAGE_CONTAINER;
  }

  @Override
  public JsonObject serialize() {
    return super.serializeCommon();
  }

  public static Builder builder() {
    return new AutoValue_CreateStorageContainerRequestData.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract CreateStorageContainerRequestData.Builder setStorageContainerName(String value);

    public abstract CreateStorageContainerRequestData.Builder setResourceGroupName(String value);

    public abstract CreateStorageContainerRequestData build();
  }
}
