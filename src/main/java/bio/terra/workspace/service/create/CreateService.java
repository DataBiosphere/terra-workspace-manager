package bio.terra.workspace.service.create;

import bio.terra.workspace.app.configuration.ApplicationConfiguration;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.service.iam.SamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateService {
  private ApplicationConfiguration configuration;
  private final CreateDAO createDAO;
  private final SamService sam;

  @Autowired
  public CreateService(
      ApplicationConfiguration configuration, CreateDAO createDAO, SamService sam) {
    this.configuration = configuration;
    this.createDAO = createDAO;
    this.sam = sam;
  }

  public CreatedWorkspace createWorkspace(CreateWorkspaceRequestBody body) {
    CreatedWorkspace workspace = new CreatedWorkspace();
    String id = body.getId().toString();

    // TODO: this is commented out due to messy merge changes (SamService API was changed in this PR
    // but this API is updated separately). This should be overwritten.
    // sam.createDefaultResource(body);
    createDAO.create(id, body.getSpendProfile());

    workspace.setId(id);
    return workspace;
  }
}
