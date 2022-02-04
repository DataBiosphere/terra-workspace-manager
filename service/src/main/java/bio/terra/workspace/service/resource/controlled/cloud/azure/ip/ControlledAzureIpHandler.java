package bio.terra.workspace.service.resource.controlled.cloud.azure.ip;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ControlledAzureIpHandler implements WsmResourceHandler {
  private static ControlledAzureIpHandler theHandler;

  public static ControlledAzureIpHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureIpHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureIpResource(dbResource);
  }
}
