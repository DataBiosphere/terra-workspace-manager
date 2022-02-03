package bio.terra.workspace.service.resource.controlled.cloud.azure.network;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ControlledAzureNetworkHandler implements WsmResourceHandler {
  private static ControlledAzureNetworkHandler theHandler;

  public static ControlledAzureNetworkHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ControlledAzureNetworkHandler());
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureNetworkResource(dbResource);
  }
}
