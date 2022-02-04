package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ControlledAzureDiskHandler implements WsmResourceHandler {
  private static ControlledAzureDiskHandler theHandler;

  public static ControlledAzureDiskHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureDiskHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureDiskResource(dbResource);
  }
}
