package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.SerializationException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.CloudType;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * WorkspaceDao includes operations on the workspace and cloud_context tables. Each cloud context
 * has separate methods - well, will have. The types and their contents are different. We anticipate
 * a small integer of cloud contexts and they share nothing, so it is not worth using interfaces or
 * inheritance to treat them in common.
 */
@Component
public class WorkspaceDao {
  // Serializing format of the GCP cloud context
  private static final long GCP_CLOUD_CONTEXT_DB_VERSION = 1;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper persistenceObjectMapper;
  private final DbUtil dbUtil;
  private final Logger logger = LoggerFactory.getLogger(WorkspaceDao.class);

  @Autowired
  public WorkspaceDao(
      NamedParameterJdbcTemplate jdbcTemplate,
      ObjectMapper persistenceObjectMapper,
      DbUtil dbUtil) {
    this.jdbcTemplate = jdbcTemplate;
    this.persistenceObjectMapper = persistenceObjectMapper;
    this.dbUtil = dbUtil;
  }

  /**
   * Persists a workspace to DB. Returns ID of persisted workspace on success.
   *
   * @param workspace all properties of the workspace to create
   * @return workspace id
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public UUID createWorkspace(Workspace workspace) {
    final String sql =
        "INSERT INTO workspace (workspace_id, display_name, description, spend_profile, properties, workspace_stage) "
            + "values (:workspace_id, :display_name, :description, :spend_profile, :properties, :workspace_stage)";

    final String workspaceId = workspace.getWorkspaceId().toString();

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId)
            .addValue("display_name", workspace.getDisplayName().orElse(null))
            .addValue("description", workspace.getDescription().orElse(null))
            .addValue(
                "spend_profile", workspace.getSpendProfileId().map(SpendProfileId::id).orElse(null))
            .addValue(
                "properties",
                workspace.getProperties().map(dbUtil::toJsonFromProperties).orElse(null))
            .addValue("workspace_stage", workspace.getWorkspaceStage().toString());
    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for workspace {}", workspaceId);
    } catch (DuplicateKeyException e) {
      throw new DuplicateWorkspaceException(
          String.format(
              "Workspace with id %s already exists - display name %s stage %s",
              workspace.getDisplayName().toString(),
              workspace.getWorkspaceStage().toString(),
              workspaceId),
          e);
    }
    return workspace.getWorkspaceId();
  }

  /**
   * @param workspaceId unique identifier of the workspace
   * @return true on successful delete, false if there's nothing to delete
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean deleteWorkspace(UUID workspaceId) {
    final String sql = "DELETE FROM workspace WHERE workspace_id = :id";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceId.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted record for workspace {}", workspaceId);
    } else {
      logger.info("No record found for delete workspace {}", workspaceId);
    }

    return deleted;
  }

  /**
   * Retrieves a workspace from database by ID.
   *
   * @param id unique idea of the workspace
   * @return workspace value object
   */
  @Transactional(
      propagation = Propagation.REQUIRED,
      isolation = Isolation.SERIALIZABLE,
      readOnly = true)
  public Workspace getWorkspace(UUID id) {
    String sql =
        "SELECT W.workspace_id, W.display_name, W.description, W.spend_profile,"
            + " W.properties, W.workspace_stage, C.context"
            + " FROM workspace W LEFT OUTER cloud_context C"
            + " OVER W.workspace_id = :id AND W.workspace_id = C.workspace_id AND C.cloud_type = 'GCP'";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id.toString());
    try {
      Workspace result =
          DataAccessUtils.requiredSingleResult(
              jdbcTemplate.query(
                  sql,
                  params,
                  (rs, rowNum) ->
                      Workspace.builder()
                          .workspaceId(UUID.fromString(rs.getString("workspace_id")))
                          .displayName(rs.getString("display_name"))
                          .description(rs.getString("description"))
                          .spendProfileId(
                              Optional.ofNullable(rs.getString("spend_profile"))
                                  .map(SpendProfileId::create)
                                  .orElse(null))
                          .properties(
                              Optional.ofNullable(rs.getString("properties"))
                                  .map(dbUtil::toPropertiesFromJson)
                                  .orElse(null))
                          .workspaceStage(WorkspaceStage.valueOf(rs.getString("workspace_stage")))
                          .gcpCloudContext(
                              Optional.ofNullable(rs.getString("context"))
                                  .map(this::deserializeGcpCloudContext)
                                  .orElse(null))
                          .build()));
      logger.debug("Retrieved workspace record {}", result);
      return result;
    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceNotFoundException(String.format("Workspace %s not found.", id.toString()));
    }
  }

  /**
   * Retrieves the GCP cloud context of the workspace.
   *
   * @param workspaceId unique id of workspace
   * @return optional GCP cloud context
   */
  @Transactional(
      propagation = Propagation.REQUIRED,
      isolation = Isolation.SERIALIZABLE,
      readOnly = true)
  public Optional<GcpCloudContext> getGcpCloudContext(UUID workspaceId) {
    String sql =
        "SELECT context FROM workspace_cloud_context "
            + "WHERE workspace_id = :workspace_id AND cloud_type = :cloud_type";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", CloudType.GCP.toString());
    try {
      return Optional.ofNullable(
          DataAccessUtils.singleResult(
              jdbcTemplate.query(
                  sql,
                  params,
                  (rs, rowNum) -> deserializeGcpCloudContext(rs.getString("context")))));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  /**
   * Create the cloud context of the workspace
   *
   * @param workspaceId unique id of the workspace
   * @param cloudContext the GCP cloud context to create
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createGcpCloudContext(UUID workspaceId, GcpCloudContext cloudContext) {
    final String sql =
        "INSERT INTO workspace_cloud_context (workspace_id, cloud_type, context)"
            + " VALUES (:context_id, :workspace_id, :cloud_type, :context::json)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", CloudType.GCP.toString())
            .addValue("context", serializeGcpCloudContext(cloudContext));
    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for GCP cloud context for workspace {}", workspaceId);
    } catch (DuplicateKeyException e) {
      throw new DuplicateCloudContextException(
          String.format("Workspace with id %s already has context for GCP cloud type", workspaceId),
          e);
    }
  }

  /**
   * Delete the GCP cloud context for a workspace
   *
   * @param workspaceId workspace of the cloud context
   */
  public void deleteGcpCloudContext(UUID workspaceId) {
    deleteGcpCloudContextWorker(workspaceId);
  }

  /**
   * Delete a cloud context for the workspace validating the projectId
   *
   * @param workspaceId workspace of the cloud context
   * @param projectId the GCP project id to validate
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void deleteGcpCloudContextWithIdCheck(UUID workspaceId, String projectId) {
    // Only perform the delete, if the project id matches the input project id
    Optional<GcpCloudContext> gcpCloudContext = getGcpCloudContext(workspaceId);
    if (gcpCloudContext.isPresent()) {
      if (StringUtils.equals(projectId, gcpCloudContext.get().getGcpProjectId())) {
        deleteGcpCloudContextWorker(workspaceId);
      }
    }
  }

  private void deleteGcpCloudContextWorker(UUID workspaceId) {
    final String sql =
        "DELETE FROM workspace_cloud_context "
            + "WHERE workspace_id = :workspace_id AND cloud_type = :cloud_type";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", CloudType.GCP);

    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted GCP cloud context for workspace {}", workspaceId);
    } else {
      logger.info("No record to delete for GCP cloud context for workspace {}", workspaceId);
    }
  }

  // -- serdes for GcpCloudContext --

  private String serializeGcpCloudContext(GcpCloudContext gcpCloudContext) {
    GcpCloudContextV1 dbContext = GcpCloudContextV1.from(gcpCloudContext.getGcpProjectId());
    try {
      return persistenceObjectMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new SerializationException("Failed to serialize GcpCloudContextV1", e);
    }
  }

  private GcpCloudContext deserializeGcpCloudContext(String json) {
    try {
      GcpCloudContextV1 result = persistenceObjectMapper.readValue(json, GcpCloudContextV1.class);
      if (result.version != GCP_CLOUD_CONTEXT_DB_VERSION) {
        throw new InvalidSerializedVersionException("Invalid serialized version");
      }
      return new GcpCloudContext(result.googleProjectId);
    } catch (JsonProcessingException e) {
      throw new SerializationException("Failed to serialize GcpCloudContextV1", e);
    }
  }

  static class GcpCloudContextV1 {
    /** Version marker to store in the db so that we can update the format later if we need to. */
    @JsonProperty final long version = GCP_CLOUD_CONTEXT_DB_VERSION;

    @JsonProperty String googleProjectId;

    public static GcpCloudContextV1 from(String googleProjectId) {
      GcpCloudContextV1 result = new GcpCloudContextV1();
      result.googleProjectId = googleProjectId;
      return result;
    }
  }
}
