package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.ResourceDescription;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public DataReferenceDao(WorkspaceManagerJdbcConfiguration jdbcConfiguration) {
    this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
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
    paramMap.put("resource_id", resourceId.orElse(null));
    paramMap.put("reference_type", referenceType.orElse(null));
    paramMap.put("reference", reference.orElse(null));

    System.out.println(reference);

    jdbcTemplate.update(sql, paramMap);
    return referenceId.toString();
  }

  public DataReferenceDescription getDataReference(UUID referenceId) {
    String sql =
        "SELECT workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference from workspace_data_reference where reference_id = :id";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", referenceId.toString());

    try {
      return jdbcTemplate.queryForObject(sql, paramMap, new DataReferenceMapper());
    } catch (EmptyResultDataAccessException e) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  public boolean deleteDataReference(UUID referenceId) {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("id", referenceId.toString());
    int rowsAffected =
        jdbcTemplate.update(
            "DELETE FROM workspace_data_reference WHERE reference_id = :id", paramMap);
    return rowsAffected > 0;
  }

  private static class ResourceDescriptionMapper implements RowMapper<ResourceDescription> {
    public ResourceDescription mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new ResourceDescription()
          .workspaceId(UUID.fromString(rs.getString("workspace_id")))
          .resourceId(UUID.fromString(rs.getString("resource_id")))
          .isVisible(rs.getBoolean("is_visible"))
          .owner(rs.getString("owner"))
          .attributes(rs.getString("attributes"));
    }
  }

  private static class DataReferenceMapper implements RowMapper<DataReferenceDescription> {
    public DataReferenceDescription mapRow(ResultSet rs, int rowNum) throws SQLException {
      ResourceDescriptionMapper resourceDescriptionMapper = new ResourceDescriptionMapper();
      return new DataReferenceDescription()
          .workspaceId(UUID.fromString(rs.getString("workspace_id")))
          .referenceId(UUID.fromString(rs.getString("reference_id")))
          .name(rs.getString("name"))
          .resourceDescription(
              rs.getString("resource_id") == null
                  ? null
                  : resourceDescriptionMapper.mapRow(rs, rowNum))
          .credentialId(rs.getString("credential_id"))
          .cloningInstructions(
              DataReferenceDescription.CloningInstructionsEnum.fromValue(
                  rs.getString("cloning_instructions")))
          .referenceType(
              DataReferenceDescription.ReferenceTypeEnum.fromValue(rs.getString("reference_type")))
          .reference(rs.getString("reference"));
    }
  }
}
