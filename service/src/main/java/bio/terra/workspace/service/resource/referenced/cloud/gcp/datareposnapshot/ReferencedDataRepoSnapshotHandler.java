package bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ReferencedDataRepoSnapshotHandler implements WsmResourceHandler {
  private static ReferencedDataRepoSnapshotHandler theHandler;

  public static ReferencedDataRepoSnapshotHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ReferencedDataRepoSnapshotHandler());
  }

  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedDataRepoSnapshotResource(dbResource);
  }
}
