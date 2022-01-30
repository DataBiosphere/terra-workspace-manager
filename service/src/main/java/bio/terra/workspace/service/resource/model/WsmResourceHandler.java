package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.db.model.DbResource;

/**
 * Interface defining the common methods for processing per-resource handlers.
 * Each resource type gets a singleton handler that implements this interface.
 * The idea is to allow code like ResourceDao to locate a resource type's handler
 * by lookup in the type enum and call it to, for example, create the resource
 * object from the DbResource object.
 */
public interface WsmResourceHandler {

  /**
   * Build a specific resource object from data out of the database.
   *
   * @param dbResource resource data from the database
   * @return resource object
   */
  WsmResource makeResourceFromDb(DbResource dbResource);


}
