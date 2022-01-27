package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ControlledBigQueryDatasetHandler implements WsmResourceHandler  {
  private static ControlledBigQueryDatasetHandler theHandler;

  public static ControlledBigQueryDatasetHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ControlledBigQueryDatasetHandler());
  }

  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledBigQueryDatasetResource(dbResource);
  }
}
