package bio.terra.workspace.service.create;

// import bio.terra.workspace.service.ping.exception.BadPingException;
import bio.terra.workspace.app.configuration.ApplicationConfiguration;
import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.service.create.exception.SamApiException;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.ResourcesApi;
import org.broadinstitute.dsde.workbench.client.sam.model.CreateResourceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CreateService {
  private ApplicationConfiguration configuration;
  private final CreateDAO createDAO;

  @Autowired
  public CreateService(ApplicationConfiguration configuration, CreateDAO createDAO) {
    this.configuration = configuration;
    this.createDAO = createDAO;
  }

  public CreatedWorkspace createWorkspace(CreateWorkspaceRequestBody body) {
    CreatedWorkspace workspace = new CreatedWorkspace();
    String id = body.getId().toString();

    // TODO: this SAM call should probably live in the folder manager, once it exists.
    ResourcesApi resourceApi = SamUtils.samResourcesApi(body.getAuthToken(), configuration.getSamAddress());
    // CreateResourceRequest createWorkspaceRequest = new CreateResourceRequest();
    // createWorkspaceRequest.setResourceId(id);
    try {
      resourceApi.createResourceWithDefaults(SamUtils.SAM_WORKSPACE_RESOURCE, body.getId().toString());
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }

    createDAO.create(id, body.getSpendProfile());

    workspace.setId(id);
    return workspace;
  }


}