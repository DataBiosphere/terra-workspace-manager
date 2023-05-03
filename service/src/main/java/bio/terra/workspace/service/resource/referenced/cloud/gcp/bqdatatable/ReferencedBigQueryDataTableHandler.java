package bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedBigQueryDataTableHandler implements WsmResourceHandler {
  private static ReferencedBigQueryDataTableHandler theHandler;

  public static ReferencedBigQueryDataTableHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ReferencedBigQueryDataTableHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedBigQueryDataTableResource(dbResource);
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new BadRequestException("generateCloudName not supported for referenced resource.");
  }
}
