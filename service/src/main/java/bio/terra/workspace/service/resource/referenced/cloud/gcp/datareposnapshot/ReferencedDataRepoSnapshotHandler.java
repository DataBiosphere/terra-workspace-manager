package bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedDataRepoSnapshotHandler implements WsmResourceHandler {
  private static ReferencedDataRepoSnapshotHandler theHandler;

  public static ReferencedDataRepoSnapshotHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ReferencedDataRepoSnapshotHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedDataRepoSnapshotResource(dbResource);
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new BadRequestException("generateCloudName not supported for referenced resource.");
  }
}
