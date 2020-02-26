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
  private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public CreateService(ApplicationConfiguration configuration, NamedParameterJdbcTemplate jdbcTemplate) {
    this.configuration = configuration;
  }

  public CreatedWorkspace createWorkspace(CreateWorkspaceRequestBody body) {
    CreatedWorkspace workspace = new CreatedWorkspace();
    String id = UUID.randomUUID().toString();
    // TODO: this SAM call should probably live in the folder manager, once it exists.
    ResourcesApi resourceApi = SamUtils.samResourcesApi(body.getAuthToken(), configuration.getSamAddress());
    // CreateResourceRequest createWorkspaceRequest = new CreateResourceRequest();
    // createWorkspaceRequest.setResourceId(id);
    try {
      resourceApi.createResourceWithDefaults("mc-workspace", id);
    } catch (ApiException apiException) {
      throw new SamApiException(apiException);
    }
    // NamedParameterJdbcTemplate jdbc = configuration.getNamedParameterJdbcTemplate(workspaceManagerJdbcConfiguration);
    Map<String,Object> paramMap = new HashMap<>();
    paramMap.put("id", id);
    if (body.getSpendProfile().isPresent()) {
      paramMap.put("spend_profile", body.getSpendProfile().get().toString());
      paramMap.put("spend_profile_settable", false);
    } else {
      paramMap.put("spend_profile", null);
      paramMap.put("spend_profile_settable", true);
    }
    jdbcTemplate.update("INSERT INTO workspace (workspace_id, spend_profile, profile_settable) values (:id, :spend_profile, :spend_profile_settable)", paramMap);
    workspace.setId(id);
    return workspace;
  }


}