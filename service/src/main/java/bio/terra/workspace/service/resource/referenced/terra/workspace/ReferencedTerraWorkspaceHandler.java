package bio.terra.workspace.service.resource.referenced.terra.workspace;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedTerraWorkspaceHandler implements WsmResourceHandler {
  private static ReferencedTerraWorkspaceHandler theHandler;

  public static ReferencedTerraWorkspaceHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ReferencedTerraWorkspaceHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedTerraWorkspaceResource(dbResource);
  }

  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new BadRequestException("generateCloudName not supported for referenced resources.");
  }
}
