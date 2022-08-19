package bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedGitRepoHandler implements WsmResourceHandler {
  private static ReferencedGitRepoHandler theHandler;

  public static ReferencedGitRepoHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ReferencedGitRepoHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedGitRepoResource(dbResource);
  }

  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new BadRequestException("generateCloudName not supported for referenced resource.");
  }
}
