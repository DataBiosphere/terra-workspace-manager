package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ControlledGcsBucketHandler implements WsmResourceHandler {
  private static ControlledGcsBucketHandler theHandler;

  public static ControlledGcsBucketHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledGcsBucketHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledGcsBucketResource(dbResource);
  }
}
