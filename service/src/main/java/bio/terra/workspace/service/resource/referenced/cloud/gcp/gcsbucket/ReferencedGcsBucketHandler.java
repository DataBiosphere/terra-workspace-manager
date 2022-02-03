package bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ReferencedGcsBucketHandler implements WsmResourceHandler {
  private static ReferencedGcsBucketHandler theHandler;

  public static ReferencedGcsBucketHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ReferencedGcsBucketHandler());
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedGcsBucketResource(dbResource);
  }
}
