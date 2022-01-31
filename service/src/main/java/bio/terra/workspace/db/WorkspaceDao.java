package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
                      .map(SpendProfileId::new)
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
            + " cast(:properties AS jsonb), :workspace_stage)";

    final String workspaceId = workspace.getWorkspaceId().toString();

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId)
            .addValue("display_name", workspace.getDisplayName().orElse(null))
            .addValue("description", workspace.getDescription().orElse(null))
            .addValue(
                "spend_profile",
                workspace.getSpendProfileId().map(SpendProfileId::getId).orElse(null))
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

  @ReadTransaction
  public Optional<Workspace> getWorkspaceIfExists(UUID id) {
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
      return Optional.of(result);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }
  /**
   * Retrieves a workspace from database by ID.
   *
   * @param id unique identifier of the workspace
   * @return workspace value object
   */
  @ReadTransaction
  public Workspace getWorkspace(UUID id) {
    return getWorkspaceIfExists(id)
        .orElseThrow(
            () ->
                new WorkspaceNotFoundException(
                    String.format("Workspace %s not found.", id.toString())));
  }

  @WriteTransaction
  public boolean updateWorkspace(
      UUID workspaceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable Map<String, String> propertyMap) {
    if (name == null && description == null && propertyMap == null) {
      throw new MissingRequiredFieldException(
          "Must specify name, description, or properties to update.");
    }

    var params = new MapSqlParameterSource();
    params.addValue("workspace_id", workspaceId.toString());

    if (name != null) {
      params.addValue("display_name", name);
    }

    if (description != null) {
      params.addValue("description", description);
    }

    if (propertyMap != null) {
      params.addValue("properties", DbSerDes.propertiesToJson(propertyMap));
    }

    String sql =
        String.format(
            "UPDATE workspace SET %s WHERE workspace_id = :workspace_id",
            DbUtils.setColumnsClause(params, "properties"));

    int rowsAffected = jdbcTemplate.update(sql, params);
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
  @Deprecated // TODO: PF-1238 remove
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
   * Create cloud context - this is used as part of CreateGcpContextFlightV2 to insert the context
   * row at the start of the create context operation.
   */
  @WriteTransaction
  public void createCloudContextStart(
      UUID workspaceId, CloudPlatform cloudPlatform, String flightId) {
    final String platform = cloudPlatform.toSql();
    final String sql =
        "INSERT INTO cloud_context (workspace_id, cloud_platform, creating_flight)"
            + " VALUES (:workspace_id, :cloud_platform, :creating_flight)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", platform)
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
   * Second part of the create cloud context - write the context. This transaction is run from a
   * flight step, so we want it to be idempotent. The algorithm is this:
   *
   * <ol>
   *   <li>try the update, searching explicitly for our created flight
   *   <li>if nothing gets updated, then maybe this is a step restart. We query the row to make sure
   *       that it exists and has a non-null context.
   * </ol>
   *
   * Since the typical case will be a successful update, we won't do the get frequently.
   *
   * @param workspaceId workspaceId part of PK for lookup
   * @param cloudPlatform platform part of PK for lookup
   * @param context serialized cloud context
   * @param flightId flight id
   */
  @WriteTransaction
  public void createCloudContextFinish(
      UUID workspaceId, CloudPlatform cloudPlatform, String context, String flightId) {
    int updatedCount = updateCloudContextWorker(workspaceId, cloudPlatform, context, flightId);

    if (updatedCount == 1) {
      // Success path
      logger.info("Updated {} cloud context for workspace {}", cloudPlatform, workspaceId);
      return;
    }

    if (updatedCount == 0) {
      // We didn't change anything. Make sure the context is there
      Optional<String> dbContext = getCloudContextWorker(workspaceId, cloudPlatform);
      if (dbContext.isPresent()) {
        logger.info(
            "{} cloud context for workspace {} already updated and unlocked",
            cloudPlatform,
            workspaceId);
        return;
      }
      throw new InternalLogicException(
          "Database corruption during cloud context creation: no row updated");
    }
    throw new InternalLogicException(
        "Database corruption during cloud context creation: multiple rows updated");
  }

  /**
   * This unconditional update is used to upgrade an existing V1 cloud context to a V2 context.
   *
   * @param workspaceId workspaceId part of PK for lookup
   * @param cloudPlatform platform part of PK for lookup
   * @param context serialized cloud context
   */
  @WriteTransaction
  public void updateCloudContext(UUID workspaceId, CloudPlatform cloudPlatform, String context) {
    int updatedCount = updateCloudContextWorker(workspaceId, cloudPlatform, context, null);
    if (updatedCount != 1) {
      throw new InternalLogicException("Cloud context not found");
    }
  }

  /**
   * Shared worker for updating cloud context
   *
   * @param workspaceId workspaceId part of PK for lookup
   * @param cloudPlatform platform part of PK for lookup
   * @param context serialized cloud context
   * @param creatingFlightId flightId - if not null, the update filters on creating_flight
   * @return number of rows updated
   */
  private int updateCloudContextWorker(
      UUID workspaceId,
      CloudPlatform cloudPlatform,
      String context,
      @Nullable String creatingFlightId) {
    String sql =
        "UPDATE cloud_context "
            + " SET context = :context::json"
            + " WHERE workspace_id = :workspace_id"
            + " AND cloud_platform = :cloud_platform";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("context", context)
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", cloudPlatform.toSql());

    if (StringUtils.isNotEmpty(creatingFlightId)) {
      sql = sql + " AND creating_flight = :creating_flight";
      params.addValue("creating_flight", creatingFlightId);
    }
    return jdbcTemplate.update(sql, params);
  }

  /**
   * Delete a cloud context for the workspace validating the flight id. This is for use by the
   * original cloud context create flight. We compare the incoming flight id with the
   * creating_flight of the cloud context. We will only delete if they match. That makes sure that
   * undoing a create does not delete a valid cloud context.
   *
   * @param workspaceId workspace of the cloud context
   * @param cloudPlatform cloud platform of the cloud context
   * @param flightId flight id making the delete request
   */
  @WriteTransaction
  public void deleteCloudContextWithFlightIdValidation(
      UUID workspaceId, CloudPlatform cloudPlatform, String flightId) {
    deleteCloudContextWorker(workspaceId, cloudPlatform, flightId);
  }

  /**
   * Delete the GCP cloud context for a workspace. This is intended for the cloud context delete
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
   * @param creatingFlightId if non-null, then it is compared to the creating_flight to make sure a
   *     conflicting create does not delete a valid cloud context. This behavior can be removed when
   *     we are able to do PF-1238.
   */
  private void deleteCloudContextWorker(
      UUID workspaceId, CloudPlatform cloudPlatform, @Nullable String creatingFlightId) {
    final String platform = cloudPlatform.toSql();
    String sql =
        "DELETE FROM cloud_context "
            + "WHERE workspace_id = :workspace_id"
            + " AND cloud_platform = :cloud_platform";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", platform);

    if (StringUtils.isNotEmpty(creatingFlightId)) {
      sql = sql + " AND creating_flight = :creating_flight";
      params.addValue("creating_flight", creatingFlightId);
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
    return getCloudContextWorker(workspaceId, cloudPlatform);
  }

  /** Retrieve the flight ID which created the cloud context for a workspace, if one exists. */
  @Deprecated // TODO: PF-1238 remove
  @ReadTransaction
  public Optional<String> getCloudContextFlightId(UUID workspaceId, CloudPlatform cloudPlatform) {
    String sql =
        "SELECT creating_flight FROM cloud_context WHERE workspace_id = :workspace_id AND cloud_platform = :cloud_platform";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", cloudPlatform.toSql());
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("creating_flight"))));
  }

  /**
   * Retrieve the serialized cloud context of an unlocked cloud context. That is, a cloud context
   * that is done being created.
   *
   * @param workspaceId workspace of the context
   * @param cloudPlatform platform context to retrieve
   * @return empty or the serialized cloud context info
   */
  private Optional<String> getCloudContextWorker(UUID workspaceId, CloudPlatform cloudPlatform) {
    String sql =
        "SELECT context FROM cloud_context"
            + " WHERE workspace_id = :workspace_id"
            + " AND cloud_platform = :cloud_platform";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", cloudPlatform.toSql());

    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("context"))));
  }

  public static class WorkspaceUserPair {
    private final UUID workspaceId;
    private final String userEmail;

    public WorkspaceUserPair(UUID workspaceId, String userEmail) {
      this.workspaceId = workspaceId;
      this.userEmail = userEmail;
    }

    public UUID getWorkspaceId() {
      return workspaceId;
    }

    public String getUserEmail() {
      return userEmail;
    }
  }

  private static final RowMapper<WorkspaceUserPair> WORKSPACE_USER_PAIR_ROW_MAPPER =
      (rs, rowNum) ->
          new WorkspaceUserPair(
              UUID.fromString(rs.getString("workspace_id")), rs.getString("assigned_user"));

  /**
   * A method for finding all users of private resources and the workspaces they have resources in.
   * The returned set will only have one entry for each {workspace, user} pair, even if a user has
   * multiple private resources in the same workspace.
   */
  @ReadTransaction
  public List<WorkspaceUserPair> getPrivateResourceUsers() {
    String sql =
        "SELECT DISTINCT workspace_id, assigned_user FROM resource WHERE assigned_user IS NOT NULL AND private_resource_state = :active_resource_state";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("active_resource_state", PrivateResourceState.ACTIVE.toSql());

    return jdbcTemplate.query(sql, params, WORKSPACE_USER_PAIR_ROW_MAPPER);
  }
}
