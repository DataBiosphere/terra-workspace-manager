package bio.terra.workspace.service.resource.controlled.cloud.azure.ip;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ControlledAzureIpHandler implements WsmResourceHandler  {
  private static ControlledAzureIpHandler theHandler;

  public static ControlledAzureIpHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ControlledAzureIpHandler());
  }

  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAzureIpResource(dbResource);
  }
}
