package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ControlledAzureRelayNamespaceHandler implements WsmResourceHandler {
  private static ControlledAzureRelayNamespaceHandler theHandler;

  public static ControlledAzureRelayNamespaceHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureRelayNamespaceHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureRelayNamespaceResource(dbResource);
  }
}
