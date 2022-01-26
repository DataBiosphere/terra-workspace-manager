package bio.terra.workspace.service.resource.controlled.azure.storage.resourcemanager.data;

import bio.terra.cloudres.azure.resourcemanager.common.ResourceManagerRequestData;
import com.azure.core.management.Region;
import com.google.gson.JsonObject;

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
public abstract class BaseStorageRequestData implements ResourceManagerRequestData {
  /** The name of the resource. */
  public abstract String name();

  /** The region of the resource. */
  public abstract Region region();

  /** The resource group of the resource. */
  public abstract String resourceGroupName();

  /**
   * Serializes this object to JSON. Not overriding {@link ResourceManagerRequestData#serialize()}
   * to ensure subclasses implement their own serialize method.
   */
  protected JsonObject serializeCommon() {
    JsonObject requestData = new JsonObject();
    requestData.addProperty("resourceGroupName", resourceGroupName());
    requestData.addProperty("name", name());
    requestData.addProperty("region", region().name());
    return requestData;
  }
}
