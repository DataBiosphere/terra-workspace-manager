package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.generated.model.DataReference;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.ResourceDescription;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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
    paramMap.put("resource_id", resourceId.orElse(null));
    try {
      paramMap.put("reference_type", resourceId.isPresent() ? null : referenceType.get());
      paramMap.put("reference", resourceId.isPresent() ? null : reference.get());
    } catch (NoSuchElementException e) {
      throw new InvalidDataReferenceException(
          "DataReference must contain either a resource ID or a reference type and a reference", e);
    }
    jdbcTemplate.update(sql, paramMap);
    return referenceId.toString();
  }

  public DataReference getDataReference(UUID referenceId) {
    String sql =
        "SELECT ref.workspace_id, ref.reference_id, ref.name, ref.resource_id, ref.credential_id, ref.cloning_instructions, ref.reference_type, ref.reference, "
            + "resource.resource_id, resource.associated_app, resource.is_visible, resource.owner, resource.attributes  "
            + "FROM workspace_data_reference AS ref "
            + "LEFT JOIN workspace_resource AS resource ON ref.resource_id = resource.resource_id "
            + "WHERE reference_id = :id "
            + "LIMIT 1";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", referenceId.toString());

    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(sql, paramMap);

    DataReference reference = new DataReference();

    // TODO: builder style
    reference.setWorkspaceId(UUID.fromString(queryOutput.get("workspace_id").toString()));
    reference.setReferenceId(UUID.fromString(queryOutput.get("reference_id").toString()));
    reference.setName(queryOutput.get("name").toString());
    reference.setReferenceType(JsonNullable.of(queryOutput.get("reference_type").toString()));
    reference.setCredentialId(JsonNullable.of(queryOutput.get("credential_id").toString()));
    reference.setCloningInstructions(queryOutput.get("cloning_instructions").toString());

    if (queryOutput.getOrDefault("resource_id", null) == null) {
      reference.setResourceDescription(null);
    } else {
      ResourceDescription resourceDescription = new ResourceDescription();
      resourceDescription.setResourceId(UUID.fromString(queryOutput.get("resource_id").toString()));
      resourceDescription.setWorkspaceId(
          UUID.fromString(queryOutput.get("workspace_id").toString()));
      resourceDescription.setApplicationId(
          JsonNullable.of(queryOutput.get("associated_app").toString()));
      // TODO: hmm
      resourceDescription.setIsVisible((Boolean) queryOutput.get("is_visible"));
      resourceDescription.setOwner(JsonNullable.of(queryOutput.get("owner").toString()));
      resourceDescription.setAttributes(JsonNullable.of(queryOutput.get("attributes").toString()));
      reference.setResourceDescription(resourceDescription);
    }
    reference.setReference(JsonNullable.of(queryOutput.get("reference").toString()));

    return reference;
  }

  public boolean deleteDataReference(UUID referenceId) {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("id", referenceId.toString());
    // TODO: this doesn't delete controlled workspace resources, just the associated data reference.
    // That's bad!
    int rowsAffected =
        jdbcTemplate.update(
            "DELETE FROM workspace_data_reference WHERE reference_id = :id", paramMap);
    return rowsAffected > 0;
  }

  public DataReferenceList enumerateDataReferences(
      String workspaceId, String owner, int offset, int limit, String filterControlled) {
    List<String> whereClauses = new ArrayList<>();
    whereClauses.add("(ref.workspace_id = :id)");
    whereClauses.add(filterControlledWhereClause(filterControlled, "ref"));
    whereClauses.add(uncontrolledOrVisibleResourcesClause(owner, "resource", "ref"));
    String filterSql = combineWhereClauses(whereClauses);
    String sql =
        "SELECT ref.workspace_id, ref.reference_id, ref.name, ref.resource_id, ref.credential_id, ref.cloning_instructions, ref.reference_type, ref.reference, "
            + "resource.resource_id, resource.associated_app, resource.is_visible, resource.owner, resource.attributes  "
            + "FROM workspace_data_reference AS ref "
            + "LEFT JOIN workspace_resource AS resource ON ref.resource_id = resource.resource_id "
            + filterSql
            + " ORDER BY ref.reference_id "
            + "OFFSET :offset "
            + "LIMIT :limit";
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("id", workspaceId);
    params.addValue("offset", offset);
    params.addValue("limit", limit);
    List<DataReference> resultList = jdbcTemplate.query(sql, params, new DataReferenceMapper());
    return new DataReferenceList().resources(resultList);
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

  private static class DataReferenceMapper implements RowMapper<DataReference> {
    public DataReference mapRow(ResultSet rs, int rowNum) throws SQLException {
      ResourceDescriptionMapper resourceDescriptionMapper = new ResourceDescriptionMapper();
      return new DataReference()
          .workspaceId(UUID.fromString(rs.getString("workspace_id")))
          .referenceId(UUID.fromString(rs.getString("reference_id")))
          .name(rs.getString("name"))
          .resourceDescription(
              rs.getString("resource_id") == null
                  ? null
                  : resourceDescriptionMapper.mapRow(rs, rowNum))
          .credentialId(rs.getString("credential_id"))
          .cloningInstructions(rs.getString("cloning_instructions"))
          .referenceType(rs.getString("reference_type"))
          .reference(rs.getString("reference"));
    }
  }

  // Returns a SQL condition as a string that selects controlled resources, uncontrolled resources,
  // or both, depending on the value of filterControlled.
  public static String filterControlledWhereClause(
      String filterControlled, String referenceTableAlias) {
    // filterControlled is one of "controlled", "uncontrolled", or "all" (enum in OpenAPI yaml).
    // This corresponds to the workspace_data_reference.resource_id column being not null, null, or
    // either, respectively.
    if (filterControlled.equals("controlled")) {
      return "(" + referenceTableAlias + ".resource_id IS NOT NULL)";
    } else if (filterControlled.equals("uncontrolled")) {
      return "(" + referenceTableAlias + ".resource_id IS NULL)";
    } else if (filterControlled.equals("all")) {
      return "";
    } else {
      // This case should not happen, as OpenAPI should enforce that this value is an enum.
      throw new ValidationException("Invalid value for filterControlled: " + filterControlled);
    }
  }

  // Returns a SQL condition as a string accepts both uncontrolled data references and visible
  // controlled references. Uncontrolled references are not tracked as resources, and their
  // existence is always visible to all workspace readers.
  public static String uncontrolledOrVisibleResourcesClause(
      String owner, String resourceTableAlias, String referenceTableAlias) {
    return "(("
        + referenceTableAlias
        + ".resource_id IS NULL) OR "
        + visibleResourcesClause(owner, resourceTableAlias)
        + ")";
  }

  // Returns a SQL condition as a string that filters out invisible controlled references.
  // References are considered 'invisible' if the is_visible column of the corresponding resource
  // is false AND the resource owner is not the user issuing the query.
  // Uncontrolled references are not tracked as resources, and their existence is always visible
  // to all workspace readers.
  public static String visibleResourcesClause(String owner, String resourceTableAlias) {
    return "("
        + resourceTableAlias
        + ".is_visible = true OR "
        + resourceTableAlias
        + ".owner = \'"
        + owner
        + "\')";
  }

  // Combines a list of String SQL conditions with the delimiter `" AND "` to create a single
  // SQL `WHERE` clause. Ignores null and empty strings.
  public static String combineWhereClauses(List<String> clauses) {
    return "WHERE ("
        + clauses.stream()
            .filter(s -> s != null && !s.isEmpty())
            .collect(Collectors.joining(" AND "))
        + ")";
  }
}
