package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.exceptions.DuplicateUserFacingIdException;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableMap;
import io.opencensus.contrib.spring.aop.Traced;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
      """
          SELECT workspace_id, user_facing_id, display_name, description, spend_profile, properties,
          workspace_stage, created_by_email, created_date
          FROM workspace
      """;

  private static final RowMapper<Workspace> WORKSPACE_ROW_MAPPER =
      (rs, rowNum) ->
          new Workspace(
              UUID.fromString(rs.getString("workspace_id")),
              rs.getString("user_facing_id"),
              rs.getString("display_name"),
              rs.getString("description"),
              Optional.ofNullable(rs.getString("spend_profile"))
                  .map(SpendProfileId::new)
                  .orElse(null),
              Optional.ofNullable(rs.getString("properties"))
                  .map(DbSerDes::jsonToProperties)
                  .orElse(Collections.emptyMap()),
              WorkspaceStage.valueOf(rs.getString("workspace_stage")),
              rs.getString("created_by_email"),
              OffsetDateTime.ofInstant(
                  rs.getTimestamp("created_date").toInstant(), ZoneId.of("UTC")));
  private final Logger logger = LoggerFactory.getLogger(WorkspaceDao.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ApplicationDao applicationDao;

  @Autowired
  public WorkspaceDao(ApplicationDao applicationDao, NamedParameterJdbcTemplate jdbcTemplate) {
    this.applicationDao = applicationDao;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Persists a workspace to DB. Returns ID of persisted workspace on success.
   *
   * @param workspace all properties of the workspace to create
   * @return workspace id
   */
  @WriteTransaction
  public UUID createWorkspace(Workspace workspace, @Nullable List<String> applicationIds) {
    final String sql =
        """
            INSERT INTO workspace (workspace_id, user_facing_id, display_name, description,
            spend_profile, properties, workspace_stage, created_by_email)
            values (:workspace_id, :user_facing_id, :display_name, :description, :spend_profile,
            cast(:properties AS jsonb), :workspace_stage, :created_by_email)
        """;

    final UUID workspaceUuid = workspace.getWorkspaceId();
    // validateUserFacingId() is called in controller. Also call here to be safe (eg see bug
    // PF-1616).
    ControllerValidationUtils.validateUserFacingId(workspace.getUserFacingId());

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("user_facing_id", workspace.getUserFacingId())
            .addValue("display_name", workspace.getDisplayName().orElse(null))
            .addValue("description", workspace.getDescription().orElse(null))
            .addValue(
                "spend_profile",
                workspace.getSpendProfileId().map(SpendProfileId::getId).orElse(null))
            .addValue("properties", DbSerDes.propertiesToJson(workspace.getProperties()))
            .addValue("workspace_stage", workspace.getWorkspaceStage().toString())
            // Only set created_by_email and don't need to set created_by_date; that is set by
            // defaultValueComputed
            .addValue("created_by_email", workspace.createdByEmail());
    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for workspace {}", workspaceUuid);
    } catch (DuplicateKeyException e) {
      String message = e.getMessage();
      if (message != null
          && message.contains(
              "duplicate key value violates unique constraint \"workspace_pkey\"")) {
        // Workspace with workspace_id already exists.
        throw new DuplicateWorkspaceException(
            String.format(
                "Workspace with id %s already exists - display name %s stage %s",
                workspaceUuid,
                workspace.getDisplayName().orElse(null),
                workspace.getWorkspaceStage()),
            e);
      } else if (message != null
          && message.contains(
              "duplicate key value violates unique constraint \"workspace_user_facing_id_key\"")) {
        // workspace_id is new, but workspace with user_facing_id already exists.
        throw new DuplicateUserFacingIdException(
            String.format(
                // "ID" instead of "userFacingId" because end user sees this.
                "Workspace with ID %s already exists", workspace.getUserFacingId()),
            e);
      } else {
        throw e;
      }
    }

    // If we have applicationIds to create, do that now.
    if (applicationIds != null) {
      applicationDao.enableWorkspaceApplications(workspaceUuid, applicationIds);
    }

    return workspaceUuid;
  }

  /**
   * @param workspaceUuid unique identifier of the workspace
   * @return true on successful delete, false if there's nothing to delete
   */
  @WriteTransaction
  public boolean deleteWorkspace(UUID workspaceUuid) {
    final String sql = "DELETE FROM workspace WHERE workspace_id = :id";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted record for workspace {}", workspaceUuid);
    } else {
      logger.info("No record found for delete workspace {}", workspaceUuid);
    }

    return deleted;
  }

  @ReadTransaction
  public Optional<Workspace> getWorkspaceIfExists(UUID uuid) {
    if (uuid == null) {
      throw new MissingRequiredFieldException("Valid workspace id is required");
    }
    String sql = WORKSPACE_SELECT_SQL + " WHERE workspace_id = :id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", uuid.toString());
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
   * @param uuid unique identifier of the workspace
   * @return workspace value object
   */
  public Workspace getWorkspace(UUID uuid) {
    return getWorkspaceIfExists(uuid)
        .orElseThrow(
            () ->
                new WorkspaceNotFoundException(
                    String.format("Workspace %s not found.", uuid.toString())));
  }

  /** Retrieves a workspace from database by userFacingId. */
  public Workspace getWorkspaceByUserFacingId(String userFacingId) {
    if (userFacingId == null || userFacingId.isEmpty()) {
      throw new MissingRequiredFieldException("userFacingId is required");
    }
    String sql = WORKSPACE_SELECT_SQL + " WHERE user_facing_id = :user_facing_id";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("user_facing_id", userFacingId);
    Workspace result;
    try {
      result =
          DataAccessUtils.requiredSingleResult(
              jdbcTemplate.query(sql, params, WORKSPACE_ROW_MAPPER));
      logger.info("Retrieved workspace record {}", result);
      return result;
    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceNotFoundException(String.format("Workspace %s not found.", userFacingId));
    }
  }

  @WriteTransaction
  public boolean updateWorkspace(
      UUID workspaceUuid,
      @Nullable String userFacingId,
      @Nullable String name,
      @Nullable String description) {
    if (userFacingId == null && name == null && description == null) {
      throw new MissingRequiredFieldException("Must specify field to update.");
    }

    var params = new MapSqlParameterSource();
    params.addValue("workspace_id", workspaceUuid.toString());

    if (userFacingId != null) {
      // validateUserFacingId() is called in controller. Also call here to be safe (eg see bug
      // PF-1616).
      ControllerValidationUtils.validateUserFacingId(userFacingId);
      params.addValue("user_facing_id", userFacingId);
    }

    if (name != null) {
      params.addValue("display_name", name);
    }

    if (description != null) {
      params.addValue("description", description);
    }

    String sql =
        String.format(
            "UPDATE workspace SET %s WHERE workspace_id = :workspace_id",
            DbUtils.setColumnsClause(params, "properties"));

    int rowsAffected;
    try {
      rowsAffected = jdbcTemplate.update(sql, params);
    } catch (DuplicateKeyException e) {
      // Workspace with user_facing_id already exists.
      String message = e.getMessage();
      if (message != null
          && message.contains(
              "duplicate key value violates unique constraint \"workspace_user_facing_id_key\"")) {
        throw new DuplicateUserFacingIdException(
            String.format(
                // "ID" instead of "userFacingId" because end user sees this.
                "Workspace with ID %s already exists", userFacingId),
            e);
      }
      throw e;
    }

    boolean updated = rowsAffected > 0;
    logger.info(
        "{} record for workspace {}",
        (updated ? "Updated" : "No Update - did not find"),
        workspaceUuid);
    return updated;
  }

  @WriteTransaction
  public void deleteWorkspaceProperties(UUID workspaceUuid, List<String> propertyKeys) {
    // get current property in this workspace id
    String selectPropertiesSql = "SELECT properties FROM workspace WHERE workspace_id = :id";
    MapSqlParameterSource propertiesParams =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    String result;

    try {
      result = jdbcTemplate.queryForObject(selectPropertiesSql, propertiesParams, String.class);
      logger.info("retrieved workspace properties {}", result);
    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceNotFoundException(String.format("Workspace %s not found.", workspaceUuid));
    }
    Map<String, String> properties =
        result == null ? new HashMap<>() : DbSerDes.jsonToProperties(result);
    for (String key : propertyKeys) {
      properties.remove(key);
    }
    final String sql =
        "UPDATE workspace SET properties = cast(:properties AS jsonb) WHERE workspace_id = :id";

    var params = new MapSqlParameterSource();
    params
        .addValue("properties", DbSerDes.propertiesToJson(properties))
        .addValue("id", workspaceUuid.toString());

    jdbcTemplate.update(sql, params);
  }

  /** Update a workspace properties */
  @WriteTransaction
  public void updateWorkspaceProperties(UUID workspaceUuid, Map<String, String> propertyMap) {
    // get current property in this workspace id
    String selectPropertiesSql = "SELECT properties FROM workspace WHERE workspace_id = :id";
    MapSqlParameterSource propertiesParams =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    String result;

    try {
      result = jdbcTemplate.queryForObject(selectPropertiesSql, propertiesParams, String.class);
      logger.info("Retrieved workspace record {}", result);

    } catch (EmptyResultDataAccessException e) {
      throw new WorkspaceNotFoundException(String.format("Workspace %s not found.", workspaceUuid));
    }

    Map<String, String> properties =
        result == null ? new HashMap<>() : DbSerDes.jsonToProperties(result);
    properties.putAll(propertyMap);
    final String sql =
        "UPDATE workspace SET properties = cast(:properties AS jsonb) WHERE workspace_id = :id";

    var params = new MapSqlParameterSource();
    params
        .addValue("properties", DbSerDes.propertiesToJson(properties))
        .addValue("id", workspaceUuid.toString());
    jdbcTemplate.update(sql, params);
  }

  /**
   * Retrieve workspaces from a list of IDs. IDs not matching workspaces will be ignored.
   *
   * @param idList List of workspaceIds to query for
   * @param offset The number of items to skip before starting to collect the result set.
   * @param limit The maximum number of items to return.
   * @return list of Workspaces corresponding to input IDs.
   */
  @Traced
  @ReadTransaction
  public List<Workspace> getWorkspacesMatchingList(Set<UUID> idList, int offset, int limit) {
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
   * Create cloud context - this is used as part of CreateGcpContextFlightV2 to insert the context
   * row at the start of the create context operation.
   */
  @WriteTransaction
  public void createCloudContextStart(
      UUID workspaceUuid, CloudPlatform cloudPlatform, String flightId) {
    final String platform = cloudPlatform.toSql();
    final String sql =
        "INSERT INTO cloud_context (workspace_id, cloud_platform, creating_flight)"
            + " VALUES (:workspace_id, :cloud_platform, :creating_flight)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("cloud_platform", platform)
            .addValue("creating_flight", flightId);
    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for {} cloud context for workspace {}", platform, workspaceUuid);
    } catch (DuplicateKeyException e) {
      throw new DuplicateCloudContextException(
          String.format(
              "Workspace with id %s already has context for %s cloud type",
              workspaceUuid, platform),
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
   * @param workspaceUuid workspaceUuid part of PK for lookup
   * @param cloudPlatform platform part of PK for lookup
   * @param context serialized cloud context
   * @param flightId flight id
   */
  @WriteTransaction
  public void createCloudContextFinish(
      UUID workspaceUuid, CloudPlatform cloudPlatform, String context, String flightId) {
    int updatedCount = updateCloudContextWorker(workspaceUuid, cloudPlatform, context, flightId);

    if (updatedCount == 1) {
      // Success path
      logger.info("Updated {} cloud context for workspace {}", cloudPlatform, workspaceUuid);
      return;
    }

    if (updatedCount == 0) {
      // We didn't change anything. Make sure the context is there
      Optional<String> dbContext = getCloudContextWorker(workspaceUuid, cloudPlatform);
      if (dbContext.isPresent()) {
        logger.info(
            "{} cloud context for workspace {} already updated and unlocked",
            cloudPlatform,
            workspaceUuid);
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
   * @param workspaceUuid workspaceUuid part of PK for lookup
   * @param cloudPlatform platform part of PK for lookup
   * @param context serialized cloud context
   */
  @WriteTransaction
  public void updateCloudContext(UUID workspaceUuid, CloudPlatform cloudPlatform, String context) {
    int updatedCount = updateCloudContextWorker(workspaceUuid, cloudPlatform, context, null);
    if (updatedCount != 1) {
      throw new InternalLogicException("Cloud context not found");
    }
  }

  /**
   * Shared worker for updating cloud context
   *
   * @param workspaceUuid workspaceUuid part of PK for lookup
   * @param cloudPlatform platform part of PK for lookup
   * @param context serialized cloud context
   * @param creatingFlightId flightId - if not null, the update filters on creating_flight
   * @return number of rows updated
   */
  private int updateCloudContextWorker(
      UUID workspaceUuid,
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
            .addValue("workspace_id", workspaceUuid.toString())
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
   * @param workspaceUuid workspace of the cloud context
   * @param cloudPlatform cloud platform of the cloud context
   * @param flightId flight id making the delete request
   */
  @WriteTransaction
  public void deleteCloudContextWithFlightIdValidation(
      UUID workspaceUuid, CloudPlatform cloudPlatform, String flightId) {
    deleteCloudContextWorker(workspaceUuid, cloudPlatform, flightId);
  }

  /**
   * Delete the GCP cloud context for a workspace. This is intended for the cloud context delete
   * flight, where we do not have idempotency issues on undo.
   *
   * @param workspaceUuid workspace of the cloud context
   * @param cloudPlatform cloud platform of the cloud context
   */
  @WriteTransaction
  public void deleteCloudContext(UUID workspaceUuid, CloudPlatform cloudPlatform) {
    deleteCloudContextWorker(workspaceUuid, cloudPlatform, null);
  }

  /**
   * Common worker for deleting cloud context
   *
   * @param workspaceUuid workspace holding the context
   * @param cloudPlatform platform of the context
   * @param creatingFlightId if non-null, then it is compared to the creating_flight to make sure a
   *     conflicting create does not delete a valid cloud context.
   */
  private void deleteCloudContextWorker(
      UUID workspaceUuid, CloudPlatform cloudPlatform, @Nullable String creatingFlightId) {
    final String platform = cloudPlatform.toSql();
    String sql =
        "DELETE FROM cloud_context "
            + "WHERE workspace_id = :workspace_id"
            + " AND cloud_platform = :cloud_platform";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("cloud_platform", platform);

    if (StringUtils.isNotEmpty(creatingFlightId)) {
      sql = sql + " AND creating_flight = :creating_flight";
      params.addValue("creating_flight", creatingFlightId);
    }

    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted {} cloud context for workspace {}", platform, workspaceUuid);
    } else {
      logger.info(
          "No record to delete for {} cloud context for workspace {}", platform, workspaceUuid);
    }
  }

  /**
   * Retrieve the serialized cloud context
   *
   * @param workspaceUuid workspace of the context
   * @param cloudPlatform platform context to retrieve
   * @return empty or the serialized cloud context info
   */
  @ReadTransaction
  public Optional<String> getCloudContext(UUID workspaceUuid, CloudPlatform cloudPlatform) {
    return getCloudContextWorker(workspaceUuid, cloudPlatform);
  }

  /**
   * Retrieve the serialized cloud context of an unlocked cloud context. That is, a cloud context
   * that is done being created.
   *
   * @param workspaceUuid workspace of the context
   * @param cloudPlatform platform context to retrieve
   * @return empty or the serialized cloud context info
   */
  private Optional<String> getCloudContextWorker(UUID workspaceUuid, CloudPlatform cloudPlatform) {
    String sql =
        "SELECT context FROM cloud_context"
            + " WHERE workspace_id = :workspace_id"
            + " AND cloud_platform = :cloud_platform";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("cloud_platform", cloudPlatform.toSql());
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("context"))));
  }

  /**
   * Retrieve all the serialized cloud contexts.
   *
   * @param cloudPlatform platform context to retrieve
   * @return a map of workspace id to serialized cloud context infos
   */
  public ImmutableMap<UUID, String> getWorkspaceIdToCloudContextMap(CloudPlatform cloudPlatform) {
    String sql =
        "SELECT context, workspace_id FROM cloud_context"
            + " WHERE cloud_platform = :cloud_platform";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("cloud_platform", cloudPlatform.toSql());

    List<WorkspaceCloudContextPair> pairs =
        jdbcTemplate.query(
            sql,
            params,
            (rs, rowNum) ->
                new WorkspaceCloudContextPair(
                    UUID.fromString(rs.getString("workspace_id")), rs.getString("context")));
    return ImmutableMap.copyOf(
        pairs.stream()
            .collect(
                Collectors.toMap(
                    WorkspaceCloudContextPair::workspaceId,
                    WorkspaceCloudContextPair::serializedCloudContext)));
  }

  private record WorkspaceCloudContextPair(UUID workspaceId, String serializedCloudContext) {}

  public static class WorkspaceUserPair {
    private final UUID workspaceUuid;
    private final String userEmail;

    public WorkspaceUserPair(UUID workspaceUuid, String userEmail) {
      this.workspaceUuid = workspaceUuid;
      this.userEmail = userEmail;
    }

    public UUID getWorkspaceId() {
      return workspaceUuid;
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
