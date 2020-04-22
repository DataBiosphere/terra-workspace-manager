package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.service.datareference.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
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
      JsonNullable<UUID> resourceId,
      JsonNullable<String> credentialId,
      String cloningInstructions,
      JsonNullable<String> referenceType,
      JsonNullable<String> reference) {
    String sql =
        "INSERT INTO workspace_data_reference (workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference) VALUES "
            + "(:workspace_id, :reference_id, :name, :resource_id, :credential_id, :cloning_instructions, :reference_type, cast(:reference AS json))";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("workspace_id", workspaceId.toString());
    paramMap.put("reference_id", referenceId.toString());
    paramMap.put("name", name);
    paramMap.put("cloning_instructions", cloningInstructions);
    paramMap.put("credential_id", credentialId.orElse(null));
    paramMap.put("resource_id", resourceId.isPresent() ? resourceId.get() : null);
    paramMap.put("reference_type", referenceType.isPresent() ? referenceType.get() : null);
    paramMap.put("reference", reference.isPresent() ? reference.get() : null);

    jdbcTemplate.update(sql, paramMap);
    return referenceId.toString();
  }

  public DataReferenceDescription getDataReference(UUID referenceId) {
    String sql =
        "SELECT workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference from workspace_data_reference where reference_id = :id";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", referenceId.toString());

    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(sql, paramMap);

    return new DataReferenceDescription()
        .workspaceId(UUID.fromString(queryOutput.get("workspace_id").toString()))
        .referenceId(UUID.fromString(queryOutput.get("reference_id").toString()))
        .cloningInstructions(
            DataReferenceDescription.CloningInstructionsEnum.fromValue(
                queryOutput.get("cloning_instructions").toString()))
        .name(queryOutput.get("name").toString())
        .referenceType(
            DataReferenceDescription.ReferenceTypeEnum.fromValue(
                queryOutput.get("reference_type").toString()))
        .reference(queryOutput.get("reference").toString())
        // TODO: query for resource once controlled resources are implemented
        .resourceDescription(null);
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
