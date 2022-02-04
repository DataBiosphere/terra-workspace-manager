package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ControlledAzureStorageHandler implements WsmResourceHandler {
  private static ControlledAzureStorageHandler theHandler;

  public static ControlledAzureStorageHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureStorageHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureStorageResource(dbResource);
  }
}
