package bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;

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
}
