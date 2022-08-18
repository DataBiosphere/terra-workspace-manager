package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.db.model.DbResource;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Interface defining the common methods for processing per-resource handlers. Each resource type
 * gets a singleton handler that implements this interface. The idea is to allow code like
 * ResourceDao to locate a resource type's handler by lookup in the type enum and call it to, for
 * example, create the resource object from the DbResource object.
 */
public interface WsmResourceHandler {

  /**
   * Build a specific resource object from data out of the database.
   *
   * @param dbResource resource data from the database
   * @return resource object
   */
  WsmResource makeResourceFromDb(DbResource dbResource);

  /**
   * Generate the resource cloud-native name for resource
   *
   * @param workspaceUuid workspace UUID, when it is not null, the generated name will attach
   *     workspace project id.
   * @param resourceName resource name
   * @return cloud-native name
   */
  String generateCloudName(@Nullable UUID workspaceUuid, String resourceName);
}
