package bio.terra.workspace.service.resource.controlled.cloud.azure.relayHybridConnection;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

public class ControlledAzureRelayHybridConnectionHandler implements WsmResourceHandler {
  private static ControlledAzureRelayHybridConnectionHandler theHandler;

  public static ControlledAzureRelayHybridConnectionHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAzureRelayHybridConnectionHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAzureRelayHybridConnectionAttributes attributes =
        DbSerDes.fromJson(
            dbResource.getAttributes(), ControlledAzureRelayHybridConnectionAttributes.class);

    var resource =
        ControlledAzureRelayHybridConnectionResource.builder()
            .namespaceName(attributes.getNamespaceName())
            .hybridConnectionName(attributes.getHybridConnectionName())
            .requiresClientAuthorization(attributes.isRequiresClientAuthorization())
            .common(new ControlledResourceFields(dbResource))
            .build();
    return resource;
  }
}
