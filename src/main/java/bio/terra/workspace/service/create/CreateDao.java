package bio.terra.workspace.service.create;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CreateDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public CreateDao(WorkspaceManagerJdbcConfiguration jdbcConfiguration) {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  public String createWorkspace(UUID workspaceId, JsonNullable<UUID> spendProfile) {
    String sql =
        "INSERT INTO workspace (workspace_id, spend_profile, profile_settable) values "
            + "(:id, :spend_profile, :spend_profile_settable)";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", workspaceId.toString());
    if (spendProfile.isPresent()) {
      paramMap.put("spend_profile", spendProfile.get().toString());
      paramMap.put("spend_profile_settable", false);
    } else {
      paramMap.put("spend_profile", null);
      paramMap.put("spend_profile_settable", true);
    }

    jdbcTemplate.update(sql, paramMap);
    return workspaceId.toString();
  }

  public boolean deleteWorkspace(UUID workspaceId) {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("id", workspaceId.toString());
    int rowsAffected =
        jdbcTemplate.update("DELETE FROM workspace WHERE workspace_id = :id", paramMap);
    return rowsAffected > 0;
  }
}
