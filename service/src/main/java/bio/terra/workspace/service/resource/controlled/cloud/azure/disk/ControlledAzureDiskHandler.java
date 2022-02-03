package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ControlledAzureDiskHandler implements WsmResourceHandler {
  private static ControlledAzureDiskHandler theHandler;

  public static ControlledAzureDiskHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ControlledAzureDiskHandler());
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureDiskResource(dbResource);
  }
}
