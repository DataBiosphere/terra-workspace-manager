package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * WorkspaceDao includes operations on the workspace and cloud_context tables. Each cloud context
 * has separate methods - well, will have. The types and their contents are different. We anticipate
 * a small integer of cloud contexts and they share nothing, so it is not worth using interfaces or
 * inheritance to treat them in common.
 */
@Component
public class WorkspaceDao {
  /** SQL query for reading a workspace */
  private static final String WORKSPACE_SELECT_SQL =
      "SELECT workspace_id, display_name, description, spend_profile, properties, workspace_stage"
          + " FROM workspace";

  private static final RowMapper<Workspace> WORKSPACE_ROW_MAPPER =
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
                      .map(DbSerDes::jsonToProperties)
                      .orElse(null))
              .workspaceStage(WorkspaceStage.valueOf(rs.getString("workspace_stage")))
              .build();
  private final Logger logger = LoggerFactory.getLogger(WorkspaceDao.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public WorkspaceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Persists a workspace to DB. Returns ID of persisted workspace on success.
   *
   * @param workspace all properties of the workspace to create
   * @return workspace id
   */
  @WriteTransaction
  public UUID createWorkspace(Workspace workspace) {
    final String sql =
        "INSERT INTO workspace (workspace_id, display_name, description, spend_profile, properties, workspace_stage) "
            + "values (:workspace_id, :display_name, :description, :spend_profile,"
            + " cast(:properties AS json), :workspace_stage)";

    final String workspaceId = workspace.getWorkspaceId().toString();

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId)
            .addValue("display_name", workspace.getDisplayName().orElse(null))
            .addValue("description", workspace.getDescription().orElse(null))
            .addValue(
                "spend_profile", workspace.getSpendProfileId().map(SpendProfileId::id).orElse(null))
            .addValue("properties", DbSerDes.propertiesToJson(workspace.getProperties()))
            .addValue("workspace_stage", workspace.getWorkspaceStage().toString());
    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for workspace {}", workspaceId);
    } catch (DuplicateKeyException e) {
      throw new DuplicateWorkspaceException(
          String.format(
              "Workspace with id %s already exists - display name %s stage %s",
              workspaceId,
              workspace.getDisplayName().toString(),
              workspace.getWorkspaceStage().toString()),
          e);
    }
    return workspace.getWorkspaceId();
  }

  /**
   * @param workspaceId unique identifier of the workspace
   * @return true on successful delete, false if there's nothing to delete
   */
  @WriteTransaction
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
   * @param id unique identifier of the workspace
   * @return workspace value object
   */
  @ReadTransaction
  public Workspace getWorkspace(UUID id) {
    if (id == null) {
      throw new MissingRequiredFieldException("Valid workspace id is required");
    }
    String sql = WORKSPACE_SELECT_SQL + " WHERE workspace_id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id.toString());
    try {
      Workspace result =
          DataAccessUtils.requiredSingleResult(
              jdbcTemplate.query(sql, params, WORKSPACE_ROW_MAPPER));
      logger.info("Retrieved workspace record {}", result);
      return result;
    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceNotFoundException(String.format("Workspace %s not found.", id.toString()));
    }
  }

  @WriteTransaction
  public boolean updateWorkspace(UUID workspaceId, String name, String description) {
    if (name == null && description == null) {
      throw new MissingRequiredFieldException("Must specify name or description to update.");
    }

    var params = new MapSqlParameterSource();

    if (name != null) {
      params.addValue("display_name", name);
    }

    if (description != null) {
      params.addValue("description", description);
    }

    return updateWorkspaceColumns(workspaceId, params);
  }

  /**
   * This is an open ended method for constructing the SQL update statement. To use it, build the
   * parameter list making the param name equal to the column name you want to update. The method
   * generates the column_name = :column_name list. It is an error if the params map is empty.
   *
   * @param workspaceId workspace identifier - not strictly necessarily, but an extra validation
   * @param columnParams sql parameters
   */
  private boolean updateWorkspaceColumns(UUID workspaceId, MapSqlParameterSource columnParams) {
    StringBuilder sb = new StringBuilder("UPDATE workspace SET ");

    sb.append(DbUtils.setColumnsClause(columnParams));
    sb.append(" WHERE workspace_id = :workspace_id");

    MapSqlParameterSource queryParams = new MapSqlParameterSource();
    queryParams
        .addValues(columnParams.getValues())
        .addValue("workspace_id", workspaceId.toString());

    int rowsAffected = jdbcTemplate.update(sb.toString(), queryParams);
    boolean updated = rowsAffected > 0;

    logger.info(
        "{} record for workspace {}",
        (updated ? "Updated" : "No Update - did not find"),
        workspaceId);

    return updated;
  }

  /**
   * Retrieve workspaces from a list of IDs. IDs not matching workspaces will be ignored.
   *
   * @param idList List of workspaceIds to query for
   * @param offset The number of items to skip before starting to collect the result set.
   * @param limit The maximum number of items to return.
   * @return list of Workspaces corresponding to input IDs.
   */
  @ReadTransaction
  public List<Workspace> getWorkspacesMatchingList(List<UUID> idList, int offset, int limit) {
    // If the incoming list is empty, the caller does not have permission to see any
    // workspaces, so we return an empty list.
    if (idList.isEmpty()) {
      return Collections.emptyList();
    }
    String sql =
        WORKSPACE_SELECT_SQL
            + " WHERE workspace_id IN (:workspace_ids) ORDER BY workspace_id OFFSET :offset LIMIT :limit";
    var params =
        new MapSqlParameterSource()
            .addValue(
                "workspace_ids", idList.stream().map(UUID::toString).collect(Collectors.toList()))
            .addValue("offset", offset)
            .addValue("limit", limit);
    return jdbcTemplate.query(sql, params, WORKSPACE_ROW_MAPPER);
  }

  /**
   * Create cloud context
   *
   * @param workspaceId unique id of the workspace
   * @param cloudPlatform cloud platform enum
   * @param context serialized cloud context attributes
   * @param flightId calling flight id. Used to ensure that we do not delete a good context while
   *     undoing from trying to create a conflicting context.
   */
  @WriteTransaction
  public void createCloudContext(
      UUID workspaceId, CloudPlatform cloudPlatform, String context, String flightId) {
    final String platform = cloudPlatform.toSql();
    final String sql =
        "INSERT INTO cloud_context (workspace_id, cloud_platform, context, creating_flight)"
            + " VALUES (:workspace_id, :cloud_platform, :context::json, :creating_flight)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", platform)
            .addValue("context", context)
            .addValue("creating_flight", flightId);
    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for {} cloud context for workspace {}", platform, workspaceId);
    } catch (DuplicateKeyException e) {
      throw new DuplicateCloudContextException(
          String.format(
              "Workspace with id %s already has context for %s cloud type", workspaceId, platform),
          e);
    }
  }

  /**
   * Delete a cloud context for the workspace validating the flight id. This is for use by the cloud
   * context create flight. We compare the incoming flight id with the creating_flight of the cloud
   * context. We will only delete if they match. That makes sure that undoing a create does not
   * delete a valid cloud context.
   *
   * @param workspaceId workspace of the cloud context
   * @param cloudPlatform cloud platform of the cloud context
   * @param flightId flight id making the delete request
   */
  @WriteTransaction
  public void deleteCloudContextWithCheck(
      UUID workspaceId, CloudPlatform cloudPlatform, String flightId) {
    deleteCloudContextWorker(workspaceId, cloudPlatform, flightId);
  }

  /**
   * Delete the GCP cloud context for a workspace This is intended for the cloud context delete
   * flight, where we do not have idempotency issues on undo.
   *
   * @param workspaceId workspace of the cloud context
   * @param cloudPlatform cloud platform of the cloud context
   */
  @WriteTransaction
  public void deleteCloudContext(UUID workspaceId, CloudPlatform cloudPlatform) {
    deleteCloudContextWorker(workspaceId, cloudPlatform, null);
  }

  /**
   * Common worker for deleting cloud context
   *
   * @param workspaceId workspace holding the context
   * @param cloudPlatform platform of the context
   * @param flightId if non-null, then it is compared to the creating_flight to make sure a
   *     conflicting create does not delete a valid cloud context
   */
  private void deleteCloudContextWorker(
      UUID workspaceId, CloudPlatform cloudPlatform, @Nullable String flightId) {
    final String platform = cloudPlatform.toSql();
    String sql =
        "DELETE FROM cloud_context "
            + "WHERE workspace_id = :workspace_id AND cloud_platform = :cloud_platform";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", platform);

    if (StringUtils.isNotEmpty(flightId)) {
      sql = sql + " AND creating_flight = :flightid";
      params.addValue("flightid", flightId);
    }

    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted {} cloud context for workspace {}", platform, workspaceId);
    } else {
      logger.info(
          "No record to delete for {} cloud context for workspace {}", platform, workspaceId);
    }
  }

  /**
   * Retrieve the serialized cloud context
   *
   * @param workspaceId workspace of the context
   * @param cloudPlatform platform context to retrieve
   * @return empty or the serialized cloud context info
   */
  @ReadTransaction
  public Optional<String> getCloudContext(UUID workspaceId, CloudPlatform cloudPlatform) {
    String sql =
        "SELECT context FROM cloud_context "
            + "WHERE workspace_id = :workspace_id AND cloud_platform = :cloud_platform";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", cloudPlatform.toSql());

    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("context"))));
  }
}
