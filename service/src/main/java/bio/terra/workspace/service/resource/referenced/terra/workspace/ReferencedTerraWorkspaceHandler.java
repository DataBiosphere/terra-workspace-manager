package bio.terra.workspace.service.resource.referenced.terra.workspace;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;

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

  public String generateCloudName(UUID workspaceUuid, String resourceName) {
    return "";
  }
}
