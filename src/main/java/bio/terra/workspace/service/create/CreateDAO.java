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
public class CreateDAO {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public CreateDAO(WorkspaceManagerJdbcConfiguration jdbcConfiguration) {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  public String create(String workspaceId, JsonNullable<UUID> spendProfile) {
    String sql =
        "INSERT INTO workspace (workspace_id, spend_profile, profile_settable) values "
            + "(:id, :spend_profile, :spend_profile_settable)";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", workspaceId);
    if (spendProfile.isPresent()) {
      paramMap.put("spend_profile", spendProfile.get().toString());
      paramMap.put("spend_profile_settable", false);
    } else {
      paramMap.put("spend_profile", null);
      paramMap.put("spend_profile_settable", true);
    }

    jdbcTemplate.update(sql, paramMap);
    return workspaceId;
  }
}
