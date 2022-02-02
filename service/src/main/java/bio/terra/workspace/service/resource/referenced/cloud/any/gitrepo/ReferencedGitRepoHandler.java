package bio.terra.workspace.service.resource.referenced.cloud.any.gitrepo;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ReferencedGitRepoHandler implements WsmResourceHandler {
  private static ReferencedGitRepoHandler theHandler;

  public static ReferencedGitRepoHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ReferencedGitRepoHandler());
  }

  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ReferencedGitRepoResource(dbResource);
  }
}
