package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.service.workspace.CloudType;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.HashMap;
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
  /** Database JSON ObjectMapper. Should not be shared with request/response serialization. */
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

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", workspaceId.toString());
    paramMap.put("spend_profile", spendProfile);
    paramMap.put("spend_profile_settable", spendProfile == null);

    try {
      jdbcTemplate.update(sql, paramMap);
    } catch (DuplicateKeyException e) {
      throw new DuplicateWorkspaceException(
          "Workspace " + workspaceId.toString() + " already exists.", e);
    }
    return workspaceId.toString();
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean deleteWorkspace(UUID workspaceId) {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("id", workspaceId.toString());
    int rowsAffected =
        jdbcTemplate.update("DELETE FROM workspace WHERE workspace_id = :id", paramMap);
    return rowsAffected > 0;
  }

  public WorkspaceDescription getWorkspace(UUID id) {
    String sql = "SELECT * FROM workspace where workspace_id = (:id)";

    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", id.toString());

    try {
      Map<String, Object> queryOutput = jdbcTemplate.queryForMap(sql, paramMap);

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

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public WorkspaceCloudContext getCloudContext(UUID workspaceId) {
    String sql =
        "SELECT cloud_type, content FROM workspace_cloud_context "
            + "WHERE workspace_id = :workspace_id;";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());
    WorkspaceCloudContext context =
        DataAccessUtils.singleResult(jdbcTemplate.query(sql, params, CLOUD_CONTEXT_ROW_MAPPER));
    return (context != null) ? context : WorkspaceCloudContext.none();
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void insertCloudContext(UUID workspaceId, WorkspaceCloudContext cloudContext) {
    String sql =
        "INSERT INTO workspace_cloud_content (workspace_id, cloud_type, content) "
            + "VALUES (:workspace_id, :cloud_type, :content::json)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", cloudContext.cloudType().toString())
            .addValue("content", CloudContextV1.from(cloudContext).serialize());
    jdbcTemplate.update(sql, params);
  }

  private static final RowMapper<WorkspaceCloudContext> CLOUD_CONTEXT_ROW_MAPPER =
      (rs, rowNum) -> {
        CloudType cloudType = CloudType.valueOf(rs.getString("cloud_type"));
        WorkspaceCloudContext result = WorkspaceCloudContext.none();
        switch (cloudType) {
          case NONE:
            result = WorkspaceCloudContext.none();
            break;
          case GOOGLE:
            CloudContextV1 cloudContext;
            try {
              cloudContext = objectMapper.readValue(rs.getString("content"), CloudContextV1.class);
            } catch (JsonProcessingException e) {
              throw new SQLException("Unable to deserialize workspace_cloud_context.content", e);
            }
            result = WorkspaceCloudContext.createGoogleContext(cloudContext.googleProjectId);
            break;
        }
        return result;
      };

  /** JSON serialization class for the workspace_cloud_context.context column. */
  private static class CloudContextV1 {
    /** Version marker to store in the db so that we can update the format later if we need to. */
    @JsonProperty long version = 1;

    @JsonProperty String googleProjectId;

    public static CloudContextV1 from(WorkspaceCloudContext workspaceCloudContext) {
      CloudContextV1 result = new CloudContextV1();
      result.googleProjectId = workspaceCloudContext.googleProjectId().orElse(null);
      return result;
    }

    /** Serialize for a JDBC string parameter value. */
    public String serialize() {
      try {
        return objectMapper.writeValueAsString(this);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Unable to serialize workspace_cloud_context.content", e);
      }
    }

    /** Deserialize from a JDBC result set string value. */
    public static CloudContextV1 deserialize(String serialized) throws SQLException {
      try {
        return objectMapper.readValue(serialized, CloudContextV1.class);
      } catch (JsonProcessingException e) {
        throw new SQLException("Unable to deserialize workspace_cloud_context.content", e);
      }
    }
  }
}
