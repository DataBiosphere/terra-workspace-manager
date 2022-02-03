package bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ReferencedBigQueryDataTableHandler implements WsmResourceHandler {
  private static ReferencedBigQueryDataTableHandler theHandler;

  public static ReferencedBigQueryDataTableHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ReferencedBigQueryDataTableHandler());
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedBigQueryDataTableResource(dbResource);
  }
}
