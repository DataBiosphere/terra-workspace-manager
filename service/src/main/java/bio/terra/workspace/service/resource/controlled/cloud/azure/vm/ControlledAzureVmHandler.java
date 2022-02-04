package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ControlledAzureVmHandler implements WsmResourceHandler {
  private static ControlledAzureVmHandler theHandler;

  public static ControlledAzureVmHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureVmHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureVmResource(dbResource);
  }
}
