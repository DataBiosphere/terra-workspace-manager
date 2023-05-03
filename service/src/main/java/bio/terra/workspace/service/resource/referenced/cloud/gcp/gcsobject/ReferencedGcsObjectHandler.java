package bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ReferencedGcsObjectHandler implements WsmResourceHandler {
  private static ReferencedGcsObjectHandler theHandler;

  public static ReferencedGcsObjectHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ReferencedGcsObjectHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedGcsObjectResource(dbResource);
  }
}
