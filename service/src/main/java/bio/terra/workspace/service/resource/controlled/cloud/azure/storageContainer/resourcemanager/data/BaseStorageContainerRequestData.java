package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.resourcemanager.data;

import bio.terra.cloudres.azure.resourcemanager.common.ResourceManagerRequestData;
import com.google.gson.JsonObject;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Extends {@link ResourceManagerRequestData} to add common fields for working with the Storage
 * Resource Provider API.
 *
 * <p>Note: Azure Storage has its own resource provider. Therefore, the type structure should be
 * independent from the Compute request types.
 *
 * <p>TODO: Consider creating a common base request type for compute and storage in
 * azure-resourcemanager-common.
 */
public abstract class BaseStorageContainerRequestData implements ResourceManagerRequestData {
  /** The name of the resource. */
  public abstract String storageContainerName();

  /** The resource group of the resource. */
  public abstract String resourceGroupName();

  /**
   * Serializes this object to JSON. Not overriding {@link ResourceManagerRequestData#serialize()}
   * to ensure subclasses implement their own serialize method.
   */
  protected JsonObject serializeCommon() {
    JsonObject requestData = new JsonObject();
    requestData.addProperty("resourceGroupName", resourceGroupName());
    requestData.addProperty("storageContainerName", storageContainerName());
    return requestData;
  }
}
