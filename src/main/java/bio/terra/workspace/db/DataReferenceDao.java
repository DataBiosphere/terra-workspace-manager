package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.ResourceDescription;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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
      UUID resourceId,
      String credentialId,
      String cloningInstructions,
      String referenceType,
      String reference) {
    String sql =
        "INSERT INTO workspace_data_reference (workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference) VALUES "
            + "(:workspace_id, :reference_id, :name, :resource_id, :credential_id, :cloning_instructions, :reference_type, cast(:reference AS json))";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("workspace_id", workspaceId.toString());
    paramMap.put("reference_id", referenceId.toString());
    paramMap.put("name", name);
    paramMap.put("cloning_instructions", cloningInstructions);
    paramMap.put("credential_id", credentialId);
    paramMap.put("resource_id", resourceId);
    paramMap.put("reference_type", referenceType);
    paramMap.put("reference", reference);

    try {
      jdbcTemplate.update(sql, paramMap);
      return referenceId.toString();
    } catch (DuplicateKeyException e) {
      throw new DuplicateDataReferenceException(
          "A data reference of this name and type already exists in the workspace");
    }
  }

  public DataReferenceDescription getDataReference(UUID workspaceId, UUID referenceId) {
    String sql =
        "SELECT workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference from workspace_data_reference where workspace_id = :workspace_id AND reference_id = :reference_id";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("workspace_id", workspaceId.toString());
    paramMap.put("reference_id", referenceId.toString());

    try {
      return jdbcTemplate.queryForObject(sql, paramMap, new DataReferenceMapper());
    } catch (EmptyResultDataAccessException e) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  public DataReferenceDescription getDataReferenceByName(
      String workspaceId, DataReferenceDescription.ReferenceTypeEnum type, String name) {
    String sql =
        "SELECT workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference from workspace_data_reference where workspace_id = :id AND reference_type = :type AND name = :name";

    Map<String, Object> paramMap = new HashMap();
    paramMap.put("id", workspaceId);
    paramMap.put("type", type.toString());
    paramMap.put("name", name);

    try {
      return jdbcTemplate.queryForObject(sql, paramMap, new DataReferenceMapper());
    } catch (EmptyResultDataAccessException e) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  public boolean isControlled(UUID referenceId) {
    String sql =
        "SELECT CASE WHEN resource_id IS NULL THEN 'false' ELSE 'true' END FROM workspace_data_reference where reference_id = :id";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", referenceId.toString());

    try {
      return jdbcTemplate.queryForObject(sql, paramMap, Boolean.class).booleanValue();
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

  public DataReferenceList enumerateDataReferences(
      String workspaceId, String owner, int offset, int limit) {
    List<String> whereClauses = new ArrayList<>();
    whereClauses.add("(ref.workspace_id = :id)");
    whereClauses.add(uncontrolledOrVisibleResourcesClause("resource", "ref"));
    String filterSql = combineWhereClauses(whereClauses);
    String sql =
        "SELECT ref.workspace_id, ref.reference_id, ref.name, ref.resource_id, ref.credential_id, ref.cloning_instructions, ref.reference_type, ref.reference,"
            + " resource.resource_id, resource.associated_app, resource.is_visible, resource.owner, resource.attributes"
            + " FROM workspace_data_reference AS ref"
            + " LEFT JOIN workspace_resource AS resource ON ref.resource_id = resource.resource_id"
            + filterSql
            + " ORDER BY ref.reference_id"
            + " OFFSET :offset"
            + " LIMIT :limit";
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("id", workspaceId);
    params.addValue("owner", owner);
    params.addValue("offset", offset);
    params.addValue("limit", limit);
    List<DataReferenceDescription> resultList =
        jdbcTemplate.query(sql, params, new DataReferenceMapper());
    return new DataReferenceList().resources(resultList);
  }

  private static class ResourceDescriptionMapper implements RowMapper<ResourceDescription> {
    public ResourceDescription mapRow(ResultSet rs, int rowNum) throws SQLException {
      String resourceId = rs.getString("resource_id");

      if (resourceId == null) {
        return null;
      } else {
        return new ResourceDescription()
            .workspaceId(UUID.fromString(rs.getString("workspace_id")))
            .resourceId(UUID.fromString(resourceId))
            .isVisible(rs.getBoolean("is_visible"))
            .owner(rs.getString("owner"))
            .attributes(rs.getString("attributes"));
      }
    }
  }

  private static class DataReferenceMapper implements RowMapper<DataReferenceDescription> {
    public DataReferenceDescription mapRow(ResultSet rs, int rowNum) throws SQLException {
      ResourceDescriptionMapper resourceDescriptionMapper = new ResourceDescriptionMapper();
      return new DataReferenceDescription()
          .workspaceId(UUID.fromString(rs.getString("workspace_id")))
          .referenceId(maybeParseUUID(rs.getString("reference_id")))
          .name(rs.getString("name"))
          .resourceDescription(resourceDescriptionMapper.mapRow(rs, rowNum))
          .credentialId(rs.getString("credential_id"))
          .cloningInstructions(
              DataReferenceDescription.CloningInstructionsEnum.fromValue(
                  rs.getString("cloning_instructions")))
          .referenceType(
              DataReferenceDescription.ReferenceTypeEnum.fromValue(rs.getString("reference_type")))
          .reference(rs.getString("reference"));
    }
  }

  public static UUID maybeParseUUID(String stringOrNull) {

    return stringOrNull == null ? null : UUID.fromString(stringOrNull);
  }

  // Returns a SQL condition as a string accepts both uncontrolled data references and visible
  // controlled references. Uncontrolled references are not tracked as resources, and their
  // existence is always visible to all workspace readers.
  public static String uncontrolledOrVisibleResourcesClause(
      String resourceTableAlias, String referenceTableAlias) {
    return "(("
        + referenceTableAlias
        + ".resource_id IS NULL) OR "
        + visibleResourcesClause(resourceTableAlias)
        + ")";
  }

  // Returns a SQL condition as a string that filters out invisible controlled references.
  // References are considered 'invisible' if the is_visible column of the corresponding resource
  // is false AND the resource owner is not the user issuing the query.
  // Uncontrolled references are not tracked as resources, and their existence is always visible
  // to all workspace readers.
  public static String visibleResourcesClause(String resourceTableAlias) {
    return "("
        + resourceTableAlias
        + ".is_visible = true OR "
        + resourceTableAlias
        + ".owner = :owner)";
  }

  // Combines a list of String SQL conditions with the delimiter `" AND "` to create a single
  // SQL `WHERE` clause. Ignores null and empty strings.
  public static String combineWhereClauses(List<String> clauses) {
    return " WHERE ("
        + clauses.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(" AND "))
        + ")";
  }
}
