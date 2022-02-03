package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ControlledAzureVmHandler implements WsmResourceHandler {
  private static ControlledAzureVmHandler theHandler;

  public static ControlledAzureVmHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ControlledAzureVmHandler());
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureVmResource(dbResource);
  }
}
