package bio.terra.workspace.service.create;

import bio.terra.workspace.app.configuration.ApplicationConfiguration;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.service.iam.Sam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateService {
  private ApplicationConfiguration configuration;
  private final CreateDAO createDAO;
  private final Sam sam;

  @Autowired
  public CreateService(ApplicationConfiguration configuration, CreateDAO createDAO, Sam sam) {
    this.configuration = configuration;
    this.createDAO = createDAO;
    this.sam = sam;
  }

  public CreatedWorkspace createWorkspace(CreateWorkspaceRequestBody body) {
    CreatedWorkspace workspace = new CreatedWorkspace();
    String id = body.getId().toString();

    // TODO: this SAM call should probably live in the folder manager, once it exists.
    sam.createDefaultResource(body);
    createDAO.create(id, body.getSpendProfile());

    workspace.setId(id);
    return workspace;
  }
}
