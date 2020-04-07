package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.model.DataReference;
import bio.terra.workspace.model.DataRepoSnapshot;
import bio.terra.workspace.service.datareference.create.exception.InvalidDataReferenceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  @Autowired
  public DataReferenceDao(
      WorkspaceManagerJdbcConfiguration jdbcConfiguration, ObjectMapper objectMapper) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
    this.objectMapper = objectMapper;
  }

  public String createDataReference(
      UUID referenceId,
      UUID workspaceId,
      String name,
      String referenceType,
      DataRepoSnapshot reference) {
    String sql =
        "INSERT INTO workspace_data_reference (workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference) VALUES "
            + "(:workspace_id, :reference_id, :name, :resource_id, :credential_id, :cloning_instructions, :reference_type, cast(:reference AS json))";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("workspace_id", workspaceId.toString());
    paramMap.put("reference_id", referenceId.toString());
    paramMap.put("name", name);
    paramMap.put("resource_id", null); // resource is uncontrolled, so leave it null //TODO:
    paramMap.put("credential_id", null); // TODO: once KeyRing exists, this won't always be null
    paramMap.put("cloning_instructions", "tbd");
    paramMap.put("reference_type", referenceType);
    try {
      paramMap.put("reference", objectMapper.writeValueAsString(reference));
    } catch (JsonProcessingException e) {
      throw new InvalidDataReferenceException("Could not write data reference to database");
    }

    jdbcTemplate.update(sql, paramMap);
    return referenceId.toString();
  }

  public DataReference getDataReference(UUID referenceId) {
    String sql =
        "SELECT workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference from workspace_data_reference where reference_id = :id";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", referenceId.toString());

    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(sql, paramMap);

    DataReference reference = new DataReference();

    reference.setWorkspaceId(UUID.fromString(queryOutput.get("workspace_id").toString()));
    reference.setReferenceId(UUID.fromString(queryOutput.get("reference_id").toString()));
    reference.setName(queryOutput.get("name").toString());
    reference.setReferenceType(queryOutput.get("reference_type").toString());

    try {
      DataRepoSnapshot snapshot =
          objectMapper.readValue(queryOutput.get("reference").toString(), DataRepoSnapshot.class);
      reference.setReference(snapshot);
    } catch (JsonProcessingException e) {
      throw new InvalidDataReferenceException("Could not read data reference from database");
    }

    return reference;
  }

  public boolean deleteDataReference(UUID referenceId) {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("id", referenceId.toString());
    int rowsAffected =
        jdbcTemplate.update(
            "DELETE FROM workspace_data_reference WHERE reference_id = :id", paramMap);
    return rowsAffected > 0;
  }
}
