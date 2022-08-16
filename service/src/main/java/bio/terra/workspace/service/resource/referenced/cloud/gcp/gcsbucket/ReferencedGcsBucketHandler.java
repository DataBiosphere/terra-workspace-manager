package bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;

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

  public String generateCloudName(UUID workspaceUuid, String resourceName) {
    return "";
  }
}
