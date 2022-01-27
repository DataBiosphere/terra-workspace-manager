package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.Optional;

public class ControlledAiNotebookHandler implements WsmResourceHandler  {
  private static ControlledAiNotebookHandler theHandler;

  public static ControlledAiNotebookHandler getHandler() {
    return Optional.ofNullable(theHandler).orElse(new ControlledAiNotebookHandler());
  }

  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    return new ControlledAiNotebookInstanceResource(dbResource);
  }
}
