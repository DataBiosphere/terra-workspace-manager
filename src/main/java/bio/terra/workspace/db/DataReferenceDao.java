package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceDescription.CloningInstructionsEnum;
import bio.terra.workspace.generated.model.DataReferenceDescription.ReferenceTypeEnum;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.FilterControlledEnum;
import bio.terra.workspace.generated.model.ResourceDescription;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

  public DataReferenceList enumerateDataReferences(
      String workspaceId,
      String owner,
      int offset,
      int limit,
      FilterControlledEnum filterControlled) {
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
    List<DataReferenceDescription> resultList =
        jdbcTemplate.query(sql, params, new DataReferenceMapper());
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
              CloningInstructionsEnum.fromValue(rs.getString("cloning_instructions")))
          .referenceType(ReferenceTypeEnum.fromValue(rs.getString("reference_type")))
          .reference(rs.getString("reference"));
    }
  }

  // Returns a SQL condition as a string that selects controlled resources, uncontrolled resources,
  // or both, depending on the value of filterControlled.
  public static String filterControlledWhereClause(
      FilterControlledEnum filterControlled, String referenceTableAlias) {
    // filterControlled is one of "controlled", "uncontrolled", or "all" (enum in OpenAPI yaml).
    // This corresponds to the workspace_data_reference.resource_id column being not null, null, or
    // either, respectively.
    switch (filterControlled) {
      case CONTROLLED:
        return "(" + referenceTableAlias + ".resource_id IS NOT NULL)";
      case UNCONTROLLED:
        return "(" + referenceTableAlias + ".resource_id IS NULL)";
      case ALL:
        return StringUtils.EMPTY;
      default:
        // This case should not happen, as OpenAPI should enforce that this value is an enum.
        // Most likely cause is that a new enum value is added to the definition but not
        // added here.
        throw new ValidationException(
            "Invalid value for filterControlled: "
                + filterControlled
                + " in filterControlledWhereClause. Has a new value been added to FilterControlledEnum?");
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
