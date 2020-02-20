package bio.terra.workspace.service.create;

// import bio.terra.workspace.service.ping.exception.BadPingException;
import bio.terra.workspace.app.configuration.ApplicationConfiguration;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CreateService {
  private ApplicationConfiguration configuration;

  @Autowired
  public CreateService(ApplicationConfiguration configuration) {
    this.configuration = configuration;
  }

  public CreatedWorkspace createWorkspace() {
    CreatedWorkspace workspace = new CreatedWorkspace();
    NamedParameterJdbcTemplate jdbc = configuration.getNamedParameterJdbcTemplate();
    String id = UUID.randomUUID().toString();
    Map<String,Object> paramMap = new HashMap<>();
    paramMap.put("id", id);
    jdbc.update("insert into workspace (workspace_id, spend_profile, profile_settable, properties) values (:id, 100, true, '{\"key\":\"value\"}')", paramMap);
    workspace.setId(id);
    return workspace;
  }


}