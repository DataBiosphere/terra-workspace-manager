package bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;

public class ReferencedBigQueryDatasetHandler implements WsmResourceHandler {
  private static ReferencedBigQueryDatasetHandler theHandler;

  public static ReferencedBigQueryDatasetHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ReferencedBigQueryDatasetHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedBigQueryDatasetResource(dbResource);
  }

  public String generateCloudName(UUID workspaceUuid, String bucketName) {
    return "";
  }
}
