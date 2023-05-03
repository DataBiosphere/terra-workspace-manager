package bio.terra.workspace.service.resource.model;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.model.DbResource;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

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
   * @param workspaceUuid workspace UUID
   * @param resourceName resource name
   * @return cloud-native name
   */
  default String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new BadRequestException(
        "generateCloudName with workspaceUuid and resourceName not supported");
  }

  /**
   * Generate the resource cloud-native name for resource
   *
   * @param workspaceUserFacingId workspace UserFacingId
   * @param resourceName resource name
   * @return cloud-native name
   */
  default String generateCloudName(@NotNull String workspaceUserFacingId, String resourceName) {
    throw new BadRequestException(
        "generateCloudName with workspaceUserFacingId and resourceName not supported");
  }
}
