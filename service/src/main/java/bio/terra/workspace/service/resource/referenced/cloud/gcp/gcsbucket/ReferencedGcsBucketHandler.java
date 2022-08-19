package bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedGcsBucketHandler implements WsmResourceHandler {
  private static ReferencedGcsBucketHandler theHandler;

  public static ReferencedGcsBucketHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ReferencedGcsBucketHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedGcsBucketResource(dbResource);
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException(
        "generateCloudName not supported for referenced resource.");
  }
}
