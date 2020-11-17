package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.generated.model.CloningInstructionsEnum;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.generated.model.ResourceDescription;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  /**
   * Database JSON ObjectMapper. Should not be shared with request/response serialization. We do not
   * want necessary changes to request/response serialization to change what's stored in the
   * database and possibly break backwards compatibility.
   */
  private static final ObjectMapper objectMapper = new ObjectMapper();
  @Autowired
  public DataReferenceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private Logger logger = LoggerFactory.getLogger(DataReferenceDao.class);

  public String createDataReference(DataReferenceRequest request, UUID referenceId) {
    String sql =
        "INSERT INTO workspace_data_reference (workspace_id, reference_id, name, cloning_instructions, reference_type, reference) VALUES "
            + "(:workspace_id, :reference_id, :name, :cloning_instructions, :reference_type, cast(:reference AS json))";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", request.workspaceId().toString())
            .addValue("reference_id", referenceId.toString())
            .addValue("name", request.name())
            .addValue("cloning_instructions", request.cloningInstructions().toString())
            .addValue("reference_type", request.referenceType().toString());
    try {
      params.addValue("reference", objectMapper.writeValueAsString(reference));
    } catch (JsonProcessingException e) {
      // TODO: add logger and print out the reference
      throw new InvalidDataReferenceException(
          "Couldn't convert reference to JSON. This... shouldn't happen.");
    }

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          String.format(
              "Inserted record for data reference %s for workspace %s", referenceId, workspaceId));
      return referenceId.toString();
    } catch (DuplicateKeyException e) {
      throw new DuplicateDataReferenceException(
          "A data reference of this name and type already exists in the workspace");
    }
  }

  public DataReference getDataReference(UUID workspaceId, UUID referenceId) {
    String sql =
        "SELECT workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference from workspace_data_reference where workspace_id = :workspace_id AND reference_id = :reference_id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("reference_id", referenceId.toString());

    try {
      DataReference ref =
          jdbcTemplate.queryForObject(sql, params, new DataReferenceMapper());
      logger.info(
          String.format(
              "Retrieved record for data reference by id %s for workspace %s",
              referenceId, workspaceId));
      return ref;
    } catch (EmptyResultDataAccessException e) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  public DataReferenceDescription getDataReferenceByName(
      UUID workspaceId, ReferenceTypeEnum type, String name) {
    String sql =
        "SELECT workspace_id, reference_id, name, resource_id, credential_id, cloning_instructions, reference_type, reference from workspace_data_reference where workspace_id = :id AND reference_type = :type AND name = :name";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", workspaceId.toString())
            .addValue("type", type.toString())
            .addValue("name", name);

    try {
      DataReferenceDescription ref =
          jdbcTemplate.queryForObject(sql, params, new DataReferenceMapper());
      logger.info(
          String.format(
              "Retrieved record for data reference by name %s and reference type %s for workspace %s",
              name, type, workspaceId));
      return ref;
    } catch (EmptyResultDataAccessException e) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  public boolean isControlled(UUID workspaceId, UUID referenceId) {
    String sql =
        "SELECT CASE WHEN resource_id IS NULL THEN 'false' ELSE 'true' END FROM workspace_data_reference where reference_id = :id AND workspace_id = :workspace_id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", referenceId.toString())
            .addValue("workspace_id", workspaceId.toString());

    try {
      return jdbcTemplate.queryForObject(sql, params, Boolean.class).booleanValue();
    } catch (EmptyResultDataAccessException e) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  public boolean deleteDataReference(UUID workspaceId, UUID referenceId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", referenceId.toString())
            .addValue("workspace_id", workspaceId.toString());
    int rowsAffected =
        jdbcTemplate.update(
            "DELETE FROM workspace_data_reference WHERE reference_id = :id AND workspace_id = :workspace_id",
            params);
    Boolean deleted = rowsAffected > 0;

    if (deleted)
      logger.info(
          String.format(
              "Deleted record for data reference %s in workspace %s",
              referenceId.toString(), workspaceId.toString()));
    else
      logger.info(
          String.format(
              "Failed to delete record for data reference %s in workspace %s",
              referenceId.toString(), workspaceId.toString()));

    return deleted;
  }

  public DataReferenceList enumerateDataReferences(
      UUID workspaceId, String owner, int offset, int limit) {
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
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", workspaceId.toString())
            .addValue("owner", owner)
            .addValue("offset", offset)
            .addValue("limit", limit);
    List<DataReferenceDescription> resultList =
        jdbcTemplate.query(sql, params, new DataReferenceMapper());
    logger.info(String.format("Retrieved data references in workspace %s", workspaceId.toString()));
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

  private class DataReferenceMapper implements RowMapper<DataReference> {
    public DataReference mapRow(ResultSet rs, int rowNum) throws SQLException {
      ResourceDescriptionMapper resourceDescriptionMapper = new ResourceDescriptionMapper();
      try {
        return DataReference.builder()
            .workspaceId(UUID.fromString(rs.getString("workspace_id")))
            .referenceId(UUID.fromString(rs.getString("reference_id")))
            .name(rs.getString("name"))
            .resourceDescription(resourceDescriptionMapper.mapRow(rs, rowNum))
            .credentialId(rs.getString("credential_id"))
            .cloningInstructions(
                CloningInstructionsEnum.fromValue(rs.getString("cloning_instructions")))
            .referenceType(ReferenceTypeEnum.fromValue(rs.getString("reference_type")))
            .reference(objectMapper.readValue(rs.getString("reference"), DataRepoSnapshot.class));
      } catch (JsonProcessingException e) {
        logger.info(
            String.format(
                "Failed to convert JSON %s to reference, with error %s",
                rs.toString(), e.getMessage()));
        throw new InvalidDataReferenceException(
            "Couldn't convert JSON to reference. This... shouldn't happen.");
      }
    }
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
