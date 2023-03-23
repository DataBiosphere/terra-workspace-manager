package bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.data;

import bio.terra.cloudres.common.CloudOperation;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.StorageManagerOperation;
import com.azure.core.management.Region;
import com.google.auto.value.AutoValue;
import com.google.gson.JsonObject;

@AutoValue
public abstract class CreateStorageAccountRequestData extends BaseStorageRequestData {

  @Override
  public CloudOperation cloudOperation() {
    return StorageManagerOperation.AZURE_CREATE_STORAGE_ACCOUNT;
  }

  @Override
  public JsonObject serialize() {
    return super.serializeCommon();
  }

  public static Builder builder() {
    return new AutoValue_CreateStorageAccountRequestData.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract CreateStorageAccountRequestData.Builder setName(String value);

    public abstract CreateStorageAccountRequestData.Builder setRegion(Region value);

    public abstract CreateStorageAccountRequestData.Builder setResourceGroupName(String value);

    public abstract CreateStorageAccountRequestData build();
  }
}
