package bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ReferencedBigQueryDatasetHandler implements WsmResourceHandler {
  private static ReferencedBigQueryDatasetHandler theHandler;

  public static ReferencedBigQueryDatasetHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ReferencedBigQueryDatasetHandler());
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedBigQueryDatasetResource(dbResource);
  }
}
