package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.db.exception.WorkspaceCloudContextNotFoundException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.model.CloudType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceCloudContext;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
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

@Component
public class WorkspaceDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper persistenceObjectMapper;
  private final DbUtil dbUtil;

  @Autowired
  public WorkspaceDao(
      NamedParameterJdbcTemplate jdbcTemplate,
      ObjectMapper persistenceObjectMapper,
      DbUtil dbUtil) {
    this.jdbcTemplate = jdbcTemplate;
    this.persistenceObjectMapper = persistenceObjectMapper;
    this.dbUtil = dbUtil;
  }

  private final Logger logger = LoggerFactory.getLogger(WorkspaceDao.class);

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
        "SELECT workspace_id, display_name, description, spend_profile, properties, workspace_stage"
            + " FROM workspace where workspace_id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id.toString());
    try {
      Workspace result =
          DataAccessUtils.requiredSingleResult(
              jdbcTemplate.query(
                  sql,
                  params,
                  (rs, rowNum) ->
                      new Workspace.Builder()
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
                          .build()));
      logger.debug("Retrieved workspace record {}", result);
      return result;
    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceNotFoundException(String.format("Workspace %s not found.", id.toString()));
    }
  }

  /**
   * Retrieves the cloud context of the workspace.
   *
   * @param workspaceId unique id of workspace
   */
  @Transactional(
      propagation = Propagation.REQUIRED,
      isolation = Isolation.SERIALIZABLE,
      readOnly = true)
  public WorkspaceCloudContext getCloudContext(UUID workspaceId, CloudType cloudType) {
    String sql =
        "SELECT context_id, context FROM workspace_cloud_context "
            + "WHERE workspace_id = :workspace_id AND cloud_type = :cloud_type";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", cloudType.toString());
    try {
      return DataAccessUtils.singleResult(
          jdbcTemplate.query(
              sql,
              params,
              (rs, rowNum) ->
                  WorkspaceCloudContext.deserialize(
                      persistenceObjectMapper,
                      cloudType,
                      UUID.fromString(rs.getString("context_id")),
                      rs.getString("context"))));
    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceCloudContextNotFoundException(
          "Cloud " + cloudType + "context not found for workspace" + workspaceId, e);
    }
  }

  /**
   * Create the cloud context of the workspace
   *
   * @param workspaceId unique id of the workspace
   * @param cloudContext the cloud context to create for the workspace
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createCloudContext(UUID workspaceId, WorkspaceCloudContext cloudContext) {
    final String sql =
        "INSERT INTO workspace_cloud_context (context_id, workspace_id, cloud_type, context)"
            + " VALUES (:context_id, :workspace_id, :cloud_type, :context::json)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("context_id", cloudContext.getCloudContextId().toString())
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", CloudType.GCP.toString())
            .addValue("context", cloudContext.serialize(persistenceObjectMapper));
    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          "Inserted record for cloud context {} for workspace {}",
          cloudContext.getCloudType(),
          workspaceId);
    } catch (DuplicateKeyException e) {
      throw new DuplicateCloudContextException(
          String.format(
              "Workspace with id %s already has context for " + "cloud type %s",
              workspaceId, cloudContext.getCloudType()),
          e);
    }
  }

  /** Delete a cloud context for the workspace */
  public void deleteCloudContext(UUID workspaceId, CloudType cloudType) {
    deleteCloudContextWorker(workspaceId, cloudType, null);
  }

  /**
   * Delete a cloud context for the workspace validating the contextId Note that we do not use this
   * method as the worker, so that we never nest Transactional annotations and only (and explicitly)
   * start transactions on the public external interfaces to the class.
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void deleteCloudContextByContextId(UUID workspaceId, CloudType cloudType, UUID contextId) {
    deleteCloudContextWorker(workspaceId, cloudType, contextId);
  }

  private void deleteCloudContextWorker(UUID workspaceId, CloudType cloudType, UUID contextId) {
    final String baseSql =
        "DELETE FROM workspace_cloud_context "
            + "WHERE workspace_id = :workspace_id AND cloud_type = :cloud_type";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", cloudType.toString());
    String sql = baseSql;

    // Add the context id filtering, if requested
    if (contextId != null) {
      sql = " AND context_id = :context_id";
      params.addValue("context_id", contextId.toString());
    }

    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted cloud context {} for workspace {}", cloudType, workspaceId);
    } else {
      logger.info(
          "No record to delete for cloud context{} for workspace {}", cloudType, workspaceId);
    }
  }
}
