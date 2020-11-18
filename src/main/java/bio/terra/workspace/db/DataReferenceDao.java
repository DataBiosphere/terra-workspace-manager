package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  /** Create a data reference in a workspace and return the reference's ID. */
  public String createDataReference(DataReferenceRequest request, UUID referenceId)
      throws DuplicateDataReferenceException {
    String sql =
        "INSERT INTO workspace_data_reference (workspace_id, reference_id, name, cloning_instructions, reference_type, reference) VALUES "
            + "(:workspace_id, :reference_id, :name, :cloning_instructions, :reference_type, cast(:reference AS json))";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", request.workspaceId().toString())
            .addValue("reference_id", referenceId.toString())
            .addValue("name", request.name())
            .addValue("cloning_instructions", request.cloningInstructions().toString())
            .addValue("reference_type", request.referenceType().toString())
            .addValue("reference", ReferenceObjectV1.from(request.referenceObject()).serialize());

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          String.format(
              "Inserted record for data reference %s for workspace %s",
              referenceId, request.workspaceId()));
      return referenceId.toString();
    } catch (DuplicateKeyException e) {
      throw new DuplicateDataReferenceException(
          "A data reference of this name and type already exists in the workspace");
    }
  }

  /** Retrieve a data reference by ID from the DB. */
  public DataReference getDataReference(UUID workspaceId, UUID referenceId) {
    String sql =
        "SELECT workspace_id, reference_id, name, cloning_instructions, reference_type, reference from workspace_data_reference where workspace_id = :workspace_id AND reference_id = :reference_id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("reference_id", referenceId.toString());

    try {
      DataReference ref = jdbcTemplate.queryForObject(sql, params, DATA_REFERENCE_ROW_MAPPER);
      logger.info(
          String.format(
              "Retrieved record for data reference by id %s for workspace %s",
              referenceId, workspaceId));
      return ref;
    } catch (EmptyResultDataAccessException e) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  /** Retrieve a data reference by name from the DB. Names are unique per workspace, per reference type. */
  public DataReference getDataReferenceByName(
      UUID workspaceId, DataReferenceType type, String name) {
    String sql =
        "SELECT workspace_id, reference_id, name, cloning_instructions, reference_type, reference from workspace_data_reference where workspace_id = :id AND reference_type = :type AND name = :name";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", workspaceId.toString())
            .addValue("type", type.toString())
            .addValue("name", name);

    try {
      DataReference ref = jdbcTemplate.queryForObject(sql, params, DATA_REFERENCE_ROW_MAPPER);
      logger.info(
          String.format(
              "Retrieved record for data reference by name %s and reference type %s for workspace %s",
              name, type, workspaceId));
      return ref;
    } catch (EmptyResultDataAccessException e) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  /** Look up whether a reference is a controlled or uncontrolled resource. */
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

  // TODO: in the future, resource_id will be a foreign key to the workspace_resources table, and we
  // should consider joining and listing those entries here.
  public List<DataReference> enumerateDataReferences(
      UUID workspaceId, String owner, int offset, int limit) {
    String sql =
        "SELECT workspace_id, reference_id, name, cloning_instructions, reference_type, reference"
            + " FROM workspace_data_reference"
            + " WHERE workspace_id = :id"
            + " ORDER BY reference_id"
            + " OFFSET :offset"
            + " LIMIT :limit";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", workspaceId.toString())
            .addValue("offset", offset)
            .addValue("limit", limit);
    List<DataReference> resultList = jdbcTemplate.query(sql, params, DATA_REFERENCE_ROW_MAPPER);
    logger.info(String.format("Retrieved data references in workspace %s", workspaceId.toString()));
    return resultList;
  }

  private static final RowMapper<DataReference> DATA_REFERENCE_ROW_MAPPER =
      (rs, rowNum) -> {
        ReferenceObjectV1 deserializedReferenceObject =
            ReferenceObjectV1.deserialize(rs.getString("reference"));
        DataReferenceType referenceType = DataReferenceType.valueOf(rs.getString("reference_type"));
        return DataReference.builder()
            .workspaceId(UUID.fromString(rs.getString("workspace_id")))
            .referenceId(UUID.fromString(rs.getString("reference_id")))
            .name(rs.getString("name"))
            .referenceType(referenceType)
            .cloningInstructions(CloningInstructions.valueOf(rs.getString("cloning_instructions")))
            .referenceObject(deserializedReferenceObject.toReferenceObject(referenceType))
            .build();
      };

  /**
   * JSON serialization class for ReferenceObjects in the workspace_data_reference.reference column.
   */
  @VisibleForTesting
  static class ReferenceObjectV1 {
    /** Version marker to store in the db so that we can update the format later if we need to. */
    @JsonProperty long version = 1;

    @JsonProperty Map<String, String> properties;

    public static DataReferenceDao.ReferenceObjectV1 from(ReferenceObject referenceObject) {
      DataReferenceDao.ReferenceObjectV1 result = new DataReferenceDao.ReferenceObjectV1();
      result.properties = referenceObject.getProperties();
      return result;
    }

    /** Serialize for a JDBC string parameter value. */
    public String serialize() {
      try {
        return objectMapper.writeValueAsString(this);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Unable to serialize workspace_data_reference.reference", e);
      }
    }

    /** Deserialize from a JDBC result set string value. */
    public static DataReferenceDao.ReferenceObjectV1 deserialize(String serialized)
        throws SQLException {
      try {
        return objectMapper.readValue(serialized, DataReferenceDao.ReferenceObjectV1.class);
      } catch (JsonProcessingException e) {
        throw new SQLException("Unable to deserialize workspace_data_reference.reference", e);
      }
    }

    /** Parse this into the actual referenceObject it represents. */
    public ReferenceObject toReferenceObject(DataReferenceType type) {
      switch (type) {
        case DATA_REPO_SNAPSHOT:
          return new SnapshotReference(
              properties.get(SnapshotReference.SNAPSHOT_REFERENCE_INSTANCE_NAME_KEY),
              properties.get(SnapshotReference.SNAPSHOT_REFERENCE_SNAPSHOT_KEY));
        default:
          throw new InvalidDataReferenceException(
              "Attempting to create unsupported reference type " + type);
      }
    }
  }
}
