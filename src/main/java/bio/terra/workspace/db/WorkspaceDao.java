package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  /**
   * Database JSON ObjectMapper. Should not be shared with request/response serialization. We do not
   * want necessary changes to request/response serialization to change what's stored in the
   * database and possibly break backwards compatibility.
   */
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public WorkspaceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public String createWorkspace(UUID workspaceId, UUID spendProfile) {
    String sql =
        "INSERT INTO workspace (workspace_id, spend_profile, profile_settable) values "
            + "(:id, :spend_profile, :spend_profile_settable)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", workspaceId.toString())
            .addValue("spend_profile", spendProfile)
            .addValue("spend_profile_settable", spendProfile == null);
    try {
      jdbcTemplate.update(sql, params);
    } catch (DuplicateKeyException e) {
      throw new DuplicateWorkspaceException(
          "Workspace " + workspaceId.toString() + " already exists.", e);
    }
    return workspaceId.toString();
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean deleteWorkspace(UUID workspaceId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceId.toString());
    int rowsAffected =
        jdbcTemplate.update("DELETE FROM workspace WHERE workspace_id = :id", params);
    return rowsAffected > 0;
  }

  public WorkspaceDescription getWorkspace(UUID id) {
    String sql = "SELECT * FROM workspace where workspace_id = (:id)";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id.toString());
    try {
      Map<String, Object> queryOutput = jdbcTemplate.queryForMap(sql, params);

      WorkspaceDescription desc = new WorkspaceDescription();
      desc.setId(UUID.fromString(queryOutput.get("workspace_id").toString()));

      if (queryOutput.getOrDefault("spend_profile", null) == null) {
        desc.setSpendProfile(null);
      } else {
        desc.setSpendProfile(UUID.fromString(queryOutput.get("spend_profile").toString()));
      }

      return desc;
    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceNotFoundException("Workspace not found.");
    }
  }

  /** Retrieves the cloud context of the workspace. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public WorkspaceCloudContext getCloudContext(UUID workspaceId) {
    String sql =
        "SELECT cloud_type, context FROM workspace_cloud_context "
            + "WHERE workspace_id = :workspace_id;";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());
    WorkspaceCloudContext context =
        DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, GOOGLE_CONTEXT_ROW_MAPPER));
    return (context == null) ? WorkspaceCloudContext.none() : context;
  }

  /** Update the cloud context of the workspace, replacing the previous cloud context. */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void updateCloudContext(UUID workspaceId, WorkspaceCloudContext cloudContext) {
    if (cloudContext.googleProjectId().isPresent()) {
      String sql =
          "INSERT INTO workspace_cloud_context (workspace_id, cloud_type, context) "
              + "VALUES (:workspace_id, :cloud_type, :context::json) "
              + "ON CONFLICT(workspace_id, cloud_type) DO UPDATE SET context = :context::json";
      MapSqlParameterSource params =
          new MapSqlParameterSource()
              .addValue("workspace_id", workspaceId.toString())
              .addValue("cloud_type", CloudType.GOOGLE.toString())
              .addValue("context", GoogleCloudContextV1.from(cloudContext).serialize());
      jdbcTemplate.update(sql, params);
    } else {
      // Clear the context if there is none.
      String sql = "DELETE FROM workspace_cloud_context WHERE workspace_id = :workspace_id";
      MapSqlParameterSource params =
          new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());
      jdbcTemplate.update(sql, params);
    }
  }

  // TODO: Once we have multiple CloudTypes, we will need to handle other contexts.
  private static final RowMapper<WorkspaceCloudContext> GOOGLE_CONTEXT_ROW_MAPPER =
      (rs, rowNum) -> {
        GoogleCloudContextV1 context = GoogleCloudContextV1.deserialize(rs.getString("context"));
        return WorkspaceCloudContext.createGoogleContext(context.googleProjectId);
      };

  @VisibleForTesting
  enum CloudType {
    GOOGLE,
  }

  /** JSON serialization class for the workspace_cloud_context.context column. */
  @VisibleForTesting
  static class GoogleCloudContextV1 {
    /** Version marker to store in the db so that we can update the format later if we need to. */
    @JsonProperty long version = 1;

    @JsonProperty String googleProjectId;

    public static GoogleCloudContextV1 from(WorkspaceCloudContext workspaceCloudContext) {
      GoogleCloudContextV1 result = new GoogleCloudContextV1();
      result.googleProjectId = workspaceCloudContext.googleProjectId().orElse(null);
      return result;
    }

    /** Serialize for a JDBC string parameter value. */
    public String serialize() {
      try {
        return objectMapper.writeValueAsString(this);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Unable to serialize workspace_cloud_context.context", e);
      }
    }

    /** Deserialize from a JDBC result set string value. */
    public static GoogleCloudContextV1 deserialize(String serialized) throws SQLException {
      try {
        return objectMapper.readValue(serialized, GoogleCloudContextV1.class);
      } catch (JsonProcessingException e) {
        throw new SQLException("Unable to deserialize workspace_cloud_context.context", e);
      }
    }
  }
}
