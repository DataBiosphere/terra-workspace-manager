package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ControlledAzureStorageHandler implements WsmResourceHandler  {
  private static ControlledAzureStorageHandler theHandler;

  public static ControlledAzureStorageHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ControlledAzureStorageHandler());
  }

  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureStorageResource(dbResource);
  }
}
