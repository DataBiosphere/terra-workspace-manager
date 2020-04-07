package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UncontrolledResourceDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public UncontrolledResourceDao(WorkspaceManagerJdbcConfiguration jdbcConfiguration) {
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  public String createUncontrolledResource(
      UUID referenceId, UUID workspaceId, String name, String referenceType, String reference) {
    String sql =
        "INSERT INTO workspace_data_reference (workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference) values "
            + "(:id, :reference_id, :name, :resource_id, :credential_id, :cloning_instructions, :reference_type, :reference)";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", workspaceId.toString());
    paramMap.put("reference_id", referenceId.toString());
    paramMap.put("name", name);
    paramMap.put("resource_id", null); // resource is uncontrolled, so leave it null
    paramMap.put("credential_id", null); // TODO: once KeyRing exists, this won't always be null
    paramMap.put("cloning_instructions", "tbd");
    paramMap.put("reference_type", referenceType);
    paramMap.put("reference", reference);

    jdbcTemplate.update(sql, paramMap);
    return referenceId.toString();
  }

  public boolean deleteUncontrolledResource(UUID referenceId) {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("id", referenceId.toString());
    int rowsAffected =
        jdbcTemplate.update(
            "DELETE FROM workspace_data_reference WHERE reference_id = :id", paramMap);
    return rowsAffected > 0;
  }
}
