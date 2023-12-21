package bio.terra.workspace.db;

import static bio.terra.workspace.service.resource.model.StewardshipType.CONTROLLED;
import static bio.terra.workspace.service.resource.model.StewardshipType.REFERENCED;
import static bio.terra.workspace.service.resource.model.StewardshipType.fromSql;
import static java.util.stream.Collectors.toList;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.db.exception.ResourceStateConflictException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Data access object for interacting with resources in the database. */
@Component
public class ResourceDao {
  private static final Logger logger = LoggerFactory.getLogger(ResourceDao.class);

  private static final String RESOURCE_SELECT_SQL_WITHOUT_WORKSPACE_ID =
      """
        SELECT workspace_id, cloud_platform, resource_id, name, description, stewardship_type,
        resource_type, exact_resource_type, cloning_instructions, attributes,
        access_scope, managed_by, associated_app, assigned_user, private_resource_state,
        resource_lineage, properties, created_date, created_by_email, region,
        state, flight_id, error
        FROM resource
      """;

  /** SQL query for reading all columns from the resource table */
  private static final String RESOURCE_SELECT_SQL =
      RESOURCE_SELECT_SQL_WITHOUT_WORKSPACE_ID + " WHERE workspace_id = :workspace_id";

  private static final RowMapper<DbResource> DB_RESOURCE_ROW_MAPPER =
      (rs, rowNum) ->
          new DbResource()
              .workspaceUuid(UUID.fromString(rs.getString("workspace_id")))
              .cloudPlatform(CloudPlatform.fromSql(rs.getString("cloud_platform")))
              .resourceId(UUID.fromString(rs.getString("resource_id")))
              .name(rs.getString("name"))
              .description(rs.getString("description"))
              .stewardshipType(fromSql(rs.getString("stewardship_type")))
              .resourceType(WsmResourceType.fromSql(rs.getString("exact_resource_type")))
              .cloningInstructions(
                  CloningInstructions.fromSql(rs.getString("cloning_instructions")))
              .attributes(rs.getString("attributes"))
              .accessScope(
                  Optional.ofNullable(rs.getString("access_scope"))
                      .map(AccessScopeType::fromSql)
                      .orElse(null))
              .managedBy(
                  Optional.ofNullable(rs.getString("managed_by"))
                      .map(ManagedByType::fromSql)
                      .orElse(null))
              .applicationId(rs.getString("associated_app"))
              .assignedUser(rs.getString("assigned_user"))
              .privateResourceState(
                  Optional.ofNullable(rs.getString("private_resource_state"))
                      .map(PrivateResourceState::fromSql)
                      .orElse(null))
              .resourceLineage(
                  Optional.ofNullable(rs.getString("resource_lineage"))
                      .map(
                          resourceLineage ->
                              DbSerDes.fromJson(
                                  resourceLineage,
                                  new TypeReference<List<ResourceLineageEntry>>() {}))
                      .orElse(null))
              .properties(DbSerDes.jsonToProperties(rs.getString("properties")))
              .createdDate(
                  OffsetDateTime.ofInstant(
                      rs.getTimestamp("created_date").toInstant(), ZoneId.of("UTC")))
              .createdByEmail(rs.getString("created_by_email"))
              .region(rs.getString("region"))
              .state(WsmResourceState.fromDb(rs.getString("state")))
              .error(StateDao.deserializeException(rs.getString("error")))
              .flightId(rs.getString("flight_id"));

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final WorkspaceActivityLogDao workspaceActivityLogDao;
  private final StateDao stateDao;

  // -- Common Resource Methods -- //

  @Autowired
  public ResourceDao(
      NamedParameterJdbcTemplate jdbcTemplate,
      WorkspaceActivityLogDao workspaceActivityLogDao,
      StateDao stateDao) {
    this.jdbcTemplate = jdbcTemplate;
    this.workspaceActivityLogDao = workspaceActivityLogDao;
    this.stateDao = stateDao;
  }

  /**
   * For flight-based resource deletion, test and make the state transition to DELETING.
   *
   * <p>The DELETING state is resolve by either a call to deleteResourceSuccess or
   * deleteResourceFailure.
   *
   * @param workspaceUuid workspace id
   * @param resourceId resource id
   * @param flightId flight id
   */
  @WriteTransaction
  public void deleteResourceStart(UUID workspaceUuid, UUID resourceId, String flightId) {
    DbResource dbResource = getDbResourceFromIds(workspaceUuid, resourceId);
    stateDao.updateState(
        dbResource,
        /* expectedFlightId= */ null,
        flightId,
        WsmResourceState.DELETING,
        /* exception= */ null);
  }

  /**
   * Successful end state of a delete operation. The operation succeeded, so it is safe to delete
   * the metadata
   *
   * @param workspaceUuid workspace id
   * @param resourceId resource id
   */
  @WriteTransaction
  public void deleteResourceSuccess(UUID workspaceUuid, UUID resourceId, String flightId) {
    DbResource dbResource = getDbResourceFromIds(workspaceUuid, resourceId);
    if (!stateDao.isResourceInState(
        dbResource, WsmResourceState.NOT_EXISTS, /* flightId= */ null)) {
      // Validate the state transition to not exists
      stateDao.updateState(
          dbResource, flightId, /* targetFlightId= */ null, WsmResourceState.NOT_EXISTS, null);
      deleteResourceWorker(workspaceUuid, resourceId, /* resourceType= */ null);
    }
  }

  /**
   * Failure end state of a delete operation. The only way to get to this code is if a delete flight
   * manages to UNDO without creating a dismal failure. That seems unlikely, but rather than assume
   * it never happens, we allow this transition.
   *
   * @param workspaceUuid workspace of the resource
   * @param resourceId identifier of the resource
   * @param flightId flight id performing the delete
   * @param exception that caused the failure
   */
  @WriteTransaction
  public void deleteResourceFailure(
      UUID workspaceUuid, UUID resourceId, String flightId, @Nullable Exception exception) {
    DbResource dbResource = getDbResourceFromIds(workspaceUuid, resourceId);
    stateDao.updateState(
        dbResource, flightId, /* targetFlightId= */ null, WsmResourceState.READY, exception);
  }

  /**
   * For deleting metadata-only referenced resources; there are no state transitions. We simply
   * delete the metadata.
   *
   * @param workspaceUuid workspace id
   * @param resourceId resource id
   */
  @WriteTransaction
  public void deleteReferencedResource(UUID workspaceUuid, UUID resourceId) {
    deleteResourceWorker(workspaceUuid, resourceId, /* resourceType= */ null);
  }

  /**
   * Delete metadata-only referenced resource with an extra check to make sure it is of the right
   * resource type.
   *
   * @param workspaceUuid workspace id
   * @param resourceId resource id
   * @param resourceType resource type to check
   * @return true if resource was deleted, false otherwise
   */
  @WriteTransaction
  public boolean deleteReferencedResourceForResourceType(
      UUID workspaceUuid, UUID resourceId, WsmResourceType resourceType) {
    return deleteResourceWorker(workspaceUuid, resourceId, resourceType);
  }

  private boolean deleteResourceWorker(
      UUID workspaceUuid, UUID resourceId, @Nullable WsmResourceType resourceType) {
    String sql =
        "DELETE FROM resource WHERE workspace_id = :workspace_id AND resource_id = :resource_id";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("resource_id", resourceId.toString());

    if (resourceType != null) {
      sql = sql + " AND exact_resource_type = :exact_resource_type";
      params.addValue("exact_resource_type", resourceType.toSql());
    }

    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    logger.info(
        "{} record for resource {} of resource type {} in workspace {}",
        (deleted ? "Deleted" : "No Delete - did not find"),
        resourceId,
        resourceType,
        workspaceUuid);
    return deleted;
  }

  /**
   * Resource enumeration
   *
   * <p>The default behavior of resource enumeration is to find all resources that are visible to
   * the caller. If the caller has gotten this far, then they are allowed to see all referenced and
   * controlled resources.
   *
   * <p>The enumeration can be filtered by a resource type. If a resource type is specified, then
   * only that type of resource is returned. The enumeration can also be filtered by a stewardship
   * type.
   *
   * @param workspaceUuid identifier for work space to enumerate
   * @param cloudResourceType filter by this cloud resource type - optional
   * @param stewardshipType filtered by this stewardship type - optional
   * @param offset starting row for result
   * @param limit maximum number of rows to return
   * @return list of resources
   */
  @ReadTransaction
  public List<WsmResource> enumerateResources(
      UUID workspaceUuid,
      @Nullable WsmResourceFamily cloudResourceType,
      @Nullable StewardshipType stewardshipType,
      int offset,
      int limit) {

    // We supply the toSql() forms of the stewardship values as parameters, so that string is only
    // defined in one place. We do not always use the stewardship values, but there is no harm
    // in having extra params.
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("offset", offset)
            .addValue("limit", limit)
            .addValue("referenced_resource", REFERENCED.toSql())
            .addValue("controlled_resource", CONTROLLED.toSql());

    StringBuilder sb = new StringBuilder(RESOURCE_SELECT_SQL);
    if (cloudResourceType != null) {
      sb.append(" AND resource_type = :resource_type");
      params.addValue("resource_type", cloudResourceType.toSql());
    }

    // There are three cases for the stewardship type filter
    // 1. If it is REFERENCED, then we ignore id list and just filter for referenced resources.
    // 2. If it is CONTROLLED, then we filter for CONTROLLED resources.
    // 3. If no filter is specified (it is null), then we want both REFERENCED and CONTROLLED
    //    resources; that is, we want the OR of 1 and 2
    boolean includeReferenced = (stewardshipType == null || stewardshipType == REFERENCED);
    boolean includeControlled = (stewardshipType == null || stewardshipType == CONTROLLED);

    final String referencedPhrase = "stewardship_type = :referenced_resource";
    final String controlledPhrase = "stewardship_type = :controlled_resource";

    sb.append(" AND ");
    if (includeReferenced && includeControlled) {
      sb.append("(").append(referencedPhrase).append(" OR ").append(controlledPhrase).append(")");
    } else if (includeReferenced) {
      sb.append(referencedPhrase);
    } else if (includeControlled) {
      sb.append(controlledPhrase);
    } else {
      // Nothing is included, so we return an empty result
      return Collections.emptyList();
    }
    sb.append(" ORDER BY name OFFSET :offset LIMIT :limit");
    List<DbResource> dbResourceList =
        jdbcTemplate.query(sb.toString(), params, DB_RESOURCE_ROW_MAPPER);

    return dbResourceList.stream().map(this::constructResource).collect(toList());
  }

  /**
   * If any of the source resource from a workspace require LINK_REFERENCE for the cloning
   * instruction, then we need to link policy instead of merging policy. This query scans the
   * resources to find out.
   *
   * @param workspaceUuid workspace to query
   * @return true if the workspace has LINK_REFERENCE cloning instructions on any resources
   */
  @ReadTransaction
  public boolean workspaceRequiresLinkReferences(UUID workspaceUuid) {
    final String sql =
        """
      SELECT COUNT(*) FROM resource
      WHERE workspace_id = :workspace_id AND cloning_instructions = :cloning_instructions
      """;

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("cloning_instructions", CloningInstructions.LINK_REFERENCE.toSql());
    Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    return (count != null && count > 0);
  }

  /**
   * Returns a list of all controlled resources in a workspace, optionally filtering by cloud
   * platform, ordered by created date newest to oldest.
   *
   * @param workspaceUuid ID of the workspace to return resources from.
   * @param cloudPlatform Optional. If present, this will only return resources from the specified
   *     cloud platform. If null, this will return resources from all cloud platforms.
   */
  @ReadTransaction
  public List<ControlledResource> listControlledResources(
      UUID workspaceUuid, @Nullable CloudPlatform cloudPlatform) {
    String sql = RESOURCE_SELECT_SQL + " AND stewardship_type = :controlled_resource ";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("controlled_resource", CONTROLLED.toSql());

    if (cloudPlatform != null) {
      sql += " AND cloud_platform = :cloud_platform";
      params.addValue("cloud_platform", cloudPlatform.toSql());
    }

    // sort desc because this is called to list resources for deletion, and we want to delete
    // resources in the reverse order they were created
    sql += " ORDER BY created_date desc";

    List<DbResource> dbResources = jdbcTemplate.query(sql, params, DB_RESOURCE_ROW_MAPPER);
    return dbResources.stream()
        .map(this::constructResource)
        .map(WsmResource::castToControlledResource)
        .collect(Collectors.toList());
  }

  /** Returns a list of all controlled resources without region field. */
  @ReadTransaction
  public List<ControlledResource> listControlledResourcesWithMissingRegion(
      @Nullable CloudPlatform cloudPlatform) {
    String sql =
        RESOURCE_SELECT_SQL_WITHOUT_WORKSPACE_ID
            + " WHERE stewardship_type = :controlled_resource AND region IS NULL";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("controlled_resource", CONTROLLED.toSql());

    if (cloudPlatform != null) {
      sql += " AND cloud_platform = :cloud_platform";
      params.addValue("cloud_platform", cloudPlatform.toSql());
    }

    List<DbResource> dbResources = jdbcTemplate.query(sql, params, DB_RESOURCE_ROW_MAPPER);
    return dbResources.stream()
        .map(this::constructResource)
        .map(WsmResource::castToControlledResource)
        .collect(Collectors.toList());
  }

  /**
   * Returns a list of all resources in the READY state in any workspace, filtering by a provided
   * list of wsm resource types.
   *
   * @param wsmResourceTypes List of wsm resource types to filter by.
   */
  @ReadTransaction
  public List<ControlledResource> listReadyResourcesByType(List<WsmResourceType> wsmResourceTypes) {
    String sql =
        RESOURCE_SELECT_SQL_WITHOUT_WORKSPACE_ID
            + " WHERE state = :state AND exact_resource_type IN (:resource_types)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("state", WsmResourceState.READY.toDb())
            .addValue(
                "resource_types",
                wsmResourceTypes.stream().map(WsmResourceType::toSql).collect(Collectors.toList()));

    List<DbResource> dbResources = jdbcTemplate.query(sql, params, DB_RESOURCE_ROW_MAPPER);
    return dbResources.stream()
        .map(this::constructResource)
        .map(WsmResource::castToControlledResource)
        .collect(Collectors.toList());
  }

  /**
   * Reads all private controlled resources assigned to a given user in a given workspace which are
   * not being cleaned up by other flights and marks them as being cleaned up by the current flight.
   *
   * @return A list of all controlled resources assigned to the given user in the given workspace
   *     which were not locked by another flight. Every item in this list will have
   *     cleanup_flight_id set to the provided flight id.
   */
  @WriteTransaction
  public List<ControlledResource> claimCleanupForWorkspacePrivateResources(
      UUID workspaceUuid, String userEmail, String flightId) {
    String filterClause =
        """
            AND stewardship_type = :controlled_resource
            AND access_scope = :access_scope
            AND assigned_user = :user_email
            AND (cleanup_flight_id IS NULL
            OR cleanup_flight_id = :flight_id)
        """;
    String readSql = RESOURCE_SELECT_SQL + filterClause;
    String writeSql =
        "UPDATE resource SET cleanup_flight_id = :flight_id WHERE workspace_id = :workspace_id"
            + filterClause;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("controlled_resource", CONTROLLED.toSql())
            .addValue("access_scope", AccessScopeType.ACCESS_SCOPE_PRIVATE.toSql())
            .addValue("user_email", userEmail)
            .addValue("flight_id", flightId);

    List<DbResource> dbResources = jdbcTemplate.query(readSql, params, DB_RESOURCE_ROW_MAPPER);
    jdbcTemplate.update(writeSql, params);
    return dbResources.stream()
        .map(this::constructResource)
        .map(WsmResource::castToControlledResource)
        .collect(Collectors.toList());
  }

  /**
   * Release all claims to clean up a user's private resources (indicated by the cleanup_flight_id
   * column of the resource table) in a workspace for the provided flight.
   */
  @WriteTransaction
  public void releasePrivateResourceCleanupClaims(
      UUID workspaceUuid, String userEmail, String flightId) {
    String writeSql =
        """
          UPDATE resource SET cleanup_flight_id = NULL
          WHERE workspace_id = :workspace_id
          AND stewardship_type = :controlled_resource
          AND access_scope = :access_scope
          AND assigned_user = :user_email
          AND cleanup_flight_id = :flight_id
        """;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("controlled_resource", CONTROLLED.toSql())
            .addValue("access_scope", AccessScopeType.ACCESS_SCOPE_PRIVATE.toSql())
            .addValue("user_email", userEmail)
            .addValue("flight_id", flightId);
    jdbcTemplate.update(writeSql, params);
  }

  /**
   * Release a single claim to clean up a user's private resource (indicated by the
   * cleanup_flight_id column of the resource table) in a workspace for the provided flight.
   */
  @WriteTransaction
  public void releasePrivateResourceCleanupClaim(
      UUID workspaceUuid, UUID resourceId, String userEmail, String flightId) {
    String writeSql =
        """
          UPDATE resource SET cleanup_flight_id = NULL
          WHERE workspace_id = :workspace_id
          AND resource_id = :resource_id
          AND stewardship_type = :controlled_resource
          AND access_scope = :access_scope
          AND assigned_user = :user_email
          AND cleanup_flight_id = :flight_id
        """;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("resource_id", resourceId.toString())
            .addValue("controlled_resource", CONTROLLED.toSql())
            .addValue("access_scope", AccessScopeType.ACCESS_SCOPE_PRIVATE.toSql())
            .addValue("user_email", userEmail)
            .addValue("flight_id", flightId);
    jdbcTemplate.update(writeSql, params);
  }

  /**
   * Deletes all controlled resources on a specified cloud platform in a workspace.
   *
   * @param workspaceUuid ID of the workspace to return resources from.
   * @param cloudPlatform The cloud platform to delete resources from. Unlike
   *     listControlledResources, this is not optional.
   * @return True if at least one resource was deleted, false if no resources were deleted.
   */
  @WriteTransaction
  public boolean deleteAllControlledResources(UUID workspaceUuid, CloudPlatform cloudPlatform) {
    String sql =
        "DELETE FROM resource WHERE workspace_id = :workspace_id AND cloud_platform = :cloud_platform AND stewardship_type = :controlled_resource";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("cloud_platform", cloudPlatform.toSql())
            .addValue("controlled_resource", CONTROLLED.toSql());
    int rowsDeleted = jdbcTemplate.update(sql, params);
    return rowsDeleted > 0;
  }

  /**
   * Retrieve a resource by ID
   *
   * @param workspaceUuid identifier of workspace for the lookup
   * @param resourceId identifier of the resource for the lookup
   * @return WsmResource object
   */
  @ReadTransaction
  public WsmResource getResource(UUID workspaceUuid, UUID resourceId) {
    final String sql = RESOURCE_SELECT_SQL + " AND resource_id = :resource_id";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("resource_id", resourceId.toString());

    return constructResource(getDbResourceRequired(sql, params));
  }

  /**
   * Retrieve a data reference by name. Names are unique per workspace.
   *
   * @param workspaceUuid identifier of workspace for the lookup
   * @param name name of the resource
   * @return WsmResource object
   */
  @ReadTransaction
  public WsmResource getResourceByName(UUID workspaceUuid, String name) {
    final String sql = RESOURCE_SELECT_SQL + " AND name = :name";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("name", name);

    return constructResource(getDbResourceRequired(sql, params));
  }

  private boolean updateResourceWorker(
      UUID workspaceUuid,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String attributes,
      @Nullable CloningInstructions cloningInstructions) {
    if (name == null && description == null && attributes == null && cloningInstructions == null) {
      return false;
    }

    var params = new MapSqlParameterSource();

    if (!StringUtils.isEmpty(name)) {
      params.addValue("name", name);
    }
    if (!StringUtils.isEmpty(description)) {
      params.addValue("description", description);
    }
    if (!StringUtils.isEmpty(attributes)) {
      params.addValue("attributes", attributes);
    }
    if (null != cloningInstructions) {
      params.addValue("cloning_instructions", cloningInstructions.toSql());
    }

    String sb =
        "UPDATE resource SET "
            + DbUtils.setColumnsClause(params, "attributes")
            + " WHERE workspace_id = :workspace_id AND resource_id = :resource_id";

    params
        .addValue("workspace_id", workspaceUuid.toString())
        .addValue("resource_id", resourceId.toString());

    int rowsAffected = jdbcTemplate.update(sb, params);
    boolean updated = rowsAffected > 0;

    logger.info(
        "{} record for resource {} in workspace {}",
        (updated ? "Updated" : "No Update - did not find"),
        resourceId,
        workspaceUuid);

    return updated;
  }

  /**
   * Update controlled resource's region
   *
   * @return whether the resource's region is successfully updated.
   */
  @WriteTransaction
  public boolean updateControlledResourceRegion(UUID resourceId, @Nullable String region) {
    var sql = "UPDATE resource SET region = :region WHERE resource_id = :resource_id";

    var params =
        new MapSqlParameterSource()
            .addValue("region", region)
            .addValue("resource_id", resourceId.toString());

    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean updated = rowsAffected > 0;

    logger.info(
        "{} region for resource {}",
        (updated ? "Updated" : "No Update - did not find"),
        resourceId);

    return updated;
  }

  @WriteTransaction
  public DbUpdater updateResourceStart(
      UUID workspaceUuid,
      UUID resourceId,
      CommonUpdateParameters commonUpdateParameters,
      String flightId) {
    DbResource dbResource = getDbResourceFromIds(workspaceUuid, resourceId);
    if (dbResource == null) {
      throw new ResourceNotFoundException(
          String.format("Cannot find resource %s in workspace %s.", resourceId, workspaceUuid));
    }

    stateDao.updateState(dbResource, null, flightId, WsmResourceState.UPDATING, null);

    DbUpdater dbUpdater =
        new DbUpdater(
            dbResource.getName(),
            dbResource.getDescription(),
            dbResource.getCloningInstructions(),
            dbResource.getAttributes());
    dbUpdater.updateFromCommonParameters(commonUpdateParameters);

    return dbUpdater;
  }

  @WriteTransaction
  public void updateResourceSuccess(
      UUID workspaceUuid, UUID resourceId, DbUpdater dbUpdater, String flightId) {

    var params = new MapSqlParameterSource();
    boolean somethingChanged = false;

    if (dbUpdater.isNameUpdated()) {
      params.addValue("name", dbUpdater.getUpdatedName());
      somethingChanged = true;
    }
    if (dbUpdater.isDescriptionUpdated()) {
      params.addValue("description", dbUpdater.getUpdatedDescription());
      somethingChanged = true;
    }
    if (dbUpdater.isCloningInstructionsUpdated()) {
      params.addValue("cloning_instructions", dbUpdater.getUpdatedCloningInstructions().toSql());
      somethingChanged = true;
    }
    if (dbUpdater.isJsonAttributesUpdated()) {
      params.addValue("attributes", dbUpdater.getUpdatedJsonAttributes());
      somethingChanged = true;
    }

    if (somethingChanged) {
      StringBuilder sb = new StringBuilder("UPDATE resource SET ");
      sb.append(DbUtils.setColumnsClause(params, "attributes"));
      sb.append(" WHERE workspace_id = :workspace_id AND resource_id = :resource_id");

      params
          .addValue("workspace_id", workspaceUuid.toString())
          .addValue("resource_id", resourceId.toString());

      int rowsAffected = 0;
      try {
        rowsAffected = jdbcTemplate.update(sb.toString(), params);
      } catch (DuplicateKeyException e) {
        throw new DuplicateResourceException(
            String.format(
                "A resource already exists in the workspace that has the same name (%s)",
                dbUpdater.getUpdatedName()));
      }
      boolean updated = rowsAffected > 0;

      logger.info(
          "{} record for resource {} in workspace {}",
          (updated ? "Updated" : "No Update - did not find"),
          resourceId,
          workspaceUuid);
    } else {
      logger.info(
          "No changes made on update of resource {} in workspace {}", resourceId, workspaceUuid);
    }

    DbResource dbResource = getDbResourceFromIds(workspaceUuid, resourceId);
    stateDao.updateState(dbResource, flightId, /* flightId= */ null, WsmResourceState.READY, null);
  }

  @WriteTransaction
  public void updateResourceFailure(UUID workspaceUuid, UUID resourceId, String flightId) {
    DbResource dbResource = getDbResourceFromIds(workspaceUuid, resourceId);
    stateDao.updateState(dbResource, flightId, /* flightId= */ null, WsmResourceState.READY, null);
  }

  // TODO: [PF-2269, PF-2556] this can go away when backfill
  // updateBigQueryDatasetDefaultTableAndPartitionLifetime
  //  stops using it and when we are defaulting zone so we do not have to update it in the notebook
  // flight.
  /**
   * Update name, description, and/or attributes of the resource.
   *
   * @param name name of the resource, may be null if it does not need to be updated
   * @param description description of the resource, may be null if it does not need to be updated
   * @param attributes resource-type specific attributes, may be null if it does not need to be
   *     updated .
   */
  @WriteTransaction
  public boolean updateResource(
      UUID workspaceUuid,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable String attributes,
      @Nullable CloningInstructions cloningInstructions) {
    return updateResourceWorker(
        workspaceUuid, resourceId, name, description, attributes, cloningInstructions);
  }

  /**
   * Create the record for a resource being created. This will return successfully if it finds an
   * existing row with the same resource id, in the CREATING state, with a matching flightId. That
   * allows creating flights to retry the metadata create step simply by re-issuing this create
   * call.
   *
   * <p>The CREATING state is resolve by either a call to createResourceSuccess or
   * createResourceFailure.
   *
   * @param resource a filled in resource object
   * @param flightId flight id performing the create
   * @throws DuplicateResourceException on a duplicate resource
   */
  @WriteTransaction
  public void createResourceStart(WsmResource resource, String flightId)
      throws DuplicateResourceException {
    DbResource dbResource =
        getDbResourceFromIds(resource.getWorkspaceId(), resource.getResourceId());
    if (stateDao.isResourceInState(dbResource, WsmResourceState.CREATING, flightId)) {
      return; // It was a retry. We are done here
    }

    // Enforce that there is a cloud context for a controlled resource
    if (resource.getStewardshipType() == CONTROLLED) {
      ControlledResource controlledResource = resource.castToControlledResource();
      CloudPlatform cloudPlatform = controlledResource.getResourceType().getCloudPlatform();
      if ((cloudPlatform != CloudPlatform.ANY)
          && !cloudContextExists(controlledResource.getWorkspaceId(), cloudPlatform)) {
        throw new CloudContextRequiredException(
            "No cloud context found in which to create a controlled resource");
      }

      // Ensure the resource is unique
      verifyUniqueness(resource);
    }
    storeResource(resource, flightId, WsmResourceState.CREATING);
  }

  /**
   * Successful completion of a create, transitions from CREATING to READY.
   *
   * @param resource the resource object successfully created
   * @param flightId flight id doing the creation
   * @return created resource
   */
  @WriteTransaction
  public WsmResource createResourceSuccess(WsmResource resource, String flightId) {
    DbResource dbResource =
        getDbResourceFromIds(resource.getWorkspaceId(), resource.getResourceId());
    stateDao.updateState(
        dbResource,
        flightId,
        /* targetFlightId= */ null,
        WsmResourceState.READY,
        /* exception= */ null);
    return getResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  /**
   * Failed completion of a create. How we handle this case depends on the resource state rule. The
   * rule is a configuration parameter that is typically an input parameter to flights.
   *
   * <p>If the rule is DELETE_ON_FAILURE, then we delete the metadata.
   *
   * <p>If the rule is BROKEN_ON_FAILURE, we update the metadata to the BROKEN state and remember
   * the exception causing the failure.
   *
   * @param resource the resource object successfully created
   * @param flightId flight id doing the creation
   * @param exception the exception for the failure
   * @param resourceStateRule how to handle failures
   */
  @WriteTransaction
  public void createResourceFailure(
      WsmResource resource,
      String flightId,
      @Nullable Exception exception,
      WsmResourceStateRule resourceStateRule) {
    DbResource dbResource =
        getDbResourceFromIds(resource.getWorkspaceId(), resource.getResourceId());
    try {
      switch (resourceStateRule) {
        case DELETE_ON_FAILURE -> {
          // There is no guarantee this is the flight which created this resource. Validate that it
          // is before attempting to delete the workspace.
          stateDao.updateState(
              dbResource, flightId, /* targetFlightId= */ null, WsmResourceState.NOT_EXISTS, null);
          deleteResourceWorker(
              resource.getWorkspaceId(), resource.getResourceId(), /* resourceType= */ null);
        }
        case BROKEN_ON_FAILURE -> {
          stateDao.updateState(
              dbResource, flightId, /* flightId= */ null, WsmResourceState.BROKEN, exception);
        }
        default -> throw new InternalLogicException("Invalid switch case");
      }
    } catch (ResourceStateConflictException e) {
      // Thrown by updateState during an invalid state transition. This indicates that the
      // caller is not the same flight that created the resource.
      logger.info(
          "Skipping resource delete in createResourceFailure. This is expected for duplicate 'createResource' requests. Cause: ",
          e);
    }
  }

  /**
   * Create a referenced resource row in the database. Sometimes we create referenced resources
   * outside of a flight. In that case, the only operation is the database insert. We store the row
   * directly in the READY state.
   *
   * @param resource a filled in referenced resource
   * @throws DuplicateResourceException on a duplicate resource_id or (workspace_id, name)
   */
  @WriteTransaction
  public void createReferencedResource(WsmResource resource) throws DuplicateResourceException {
    if (resource.getStewardshipType() != REFERENCED) {
      throw new InternalLogicException("Expected a referenced resource");
    }
    storeResource(resource, null, WsmResourceState.READY);
  }

  private boolean cloudContextExists(UUID workspaceUuid, CloudPlatform cloudPlatform) {
    // Check existence of the cloud context for this workspace
    final String sql =
        """
      SELECT COUNT(*) FROM cloud_context
      WHERE workspace_id = :workspace_id AND cloud_platform = :cloud_platform
      """;

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("cloud_platform", cloudPlatform.toSql());
    Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    return (count != null && count > 0);
  }

  // Verify that the resource to be created doesn't already exist according to per-resource type
  // uniqueness rules. This prevents a race condition allowing a new resource to point to the same
  // cloud artifact as another, even if it has a different resource name and ID.
  private void verifyUniqueness(WsmResource resource) {
    ControlledResource controlledResource = resource.castToControlledResource();
    Optional<UniquenessCheckAttributes> optionalUniquenessCheck =
        controlledResource.getUniquenessCheckAttributes();

    if (optionalUniquenessCheck.isPresent()) {
      UniquenessCheckAttributes uniquenessCheck = optionalUniquenessCheck.get();
      StringBuilder queryBuilder =
          new StringBuilder()
              .append(
                  "SELECT COUNT(1) FROM resource WHERE exact_resource_type = :exact_resource_type");
      MapSqlParameterSource params =
          new MapSqlParameterSource()
              .addValue("exact_resource_type", controlledResource.getResourceType().toSql());

      if (uniquenessCheck.getUniquenessScope() == UniquenessScope.WORKSPACE) {
        queryBuilder.append(" AND workspace_id = :workspace_id");
        params.addValue("workspace_id", controlledResource.getWorkspaceId().toString());
      }

      for (Pair<String, String> pair : uniquenessCheck.getParameters()) {
        String name = pair.getKey();
        queryBuilder.append(String.format(" AND attributes->>'%s' = :%s", name, name));
        params.addValue(name, pair.getValue());
      }

      Integer matchingCount =
          jdbcTemplate.queryForObject(queryBuilder.toString(), params, Integer.class);
      if (matchingCount != null && matchingCount > 0) {
        throw new DuplicateResourceException("A resource with matching attributes already exists");
      }
    }
  }

  /**
   * Set the private_resource_state of a single private controlled resource. To set the state for
   * all a user's private resources in a workspace, use {@link
   * #setPrivateResourcesStateForWorkspaceUser(UUID, String, PrivateResourceState, Optional)}
   */
  @WriteTransaction
  public void setPrivateResourceState(
      ControlledResource resource, PrivateResourceState privateResourceState) {
    final String sql =
        """
          UPDATE resource SET private_resource_state = :private_resource_state
          WHERE workspace_id = :workspace_id
          AND resource_id = :resource_id
          AND access_scope = :private_access_scope
        """;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("private_resource_state", privateResourceState.toSql())
            .addValue("workspace_id", resource.getWorkspaceId().toString())
            .addValue("resource_id", resource.getResourceId().toString())
            .addValue("private_access_scope", AccessScopeType.ACCESS_SCOPE_PRIVATE.toSql());
    jdbcTemplate.update(sql, params);
  }

  /**
   * Sets the private_resource_state of all resources in a single workspace assigned to a user. To
   * set the private_resource_state of a single resource, use {@link
   * #setPrivateResourceState(ControlledResource, PrivateResourceState)}
   */
  @WriteTransaction
  public void setPrivateResourcesStateForWorkspaceUser(
      UUID workspaceUuid,
      String userEmail,
      PrivateResourceState state,
      Optional<String> maybeFlightId) {
    String sql =
        """
          UPDATE resource SET private_resource_state = :private_resource_state
          WHERE workspace_id = :workspace_id AND assigned_user = :user_email
         """;
    if (maybeFlightId.isPresent()) {
      sql += " AND cleanup_flight_id = :flight_id";
    }
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("private_resource_state", state.toSql())
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("user_email", userEmail);
    maybeFlightId.ifPresent(flightId -> params.addValue("flight_id", flightId));
    jdbcTemplate.update(sql, params);
  }

  /**
   * Wait for a resource row to appear in the database. This is used so async resource creation
   * calls return after the database row has been inserted. That allows clients to see the resource
   * with a resource enumeration.
   *
   * @param workspaceUuid workspace where the resource is being created
   * @param resourceId resource being created
   */
  @ReadTransaction
  public boolean resourceExists(UUID workspaceUuid, UUID resourceId) {
    final String sql =
        """
            SELECT COUNT(1) FROM resource
            WHERE workspace_id = :workspace_id AND resource_id = :resource_id
            """;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("resource_id", resourceId.toString());

    Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    return (count != null && count > 0);
  }

  @ReadTransaction
  public boolean resourceExists(UUID workspaceUuid, String name) {
    final String sql =
        """
                SELECT COUNT(1) FROM resource
                WHERE workspace_id = :workspace_id AND name = :name
                """;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("name", name);
    Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    return (count != null && count > 0);
  }

  private void storeResource(WsmResource resource, String flightId, WsmResourceState state) {
    final String sql =
        """
        INSERT INTO resource (workspace_id, cloud_platform, resource_id, name, description,
          stewardship_type, exact_resource_type, resource_type, cloning_instructions, attributes,
          access_scope, managed_by, associated_app, assigned_user, private_resource_state,
          resource_lineage, properties, created_by_email, region,
          state, flight_id)
        VALUES (:workspace_id, :cloud_platform, :resource_id, :name, :description,
          :stewardship_type, :exact_resource_type, :resource_type, :cloning_instructions,
          cast(:attributes AS jsonb), :access_scope, :managed_by, :associated_app, :assigned_user,
          :private_resource_state, :resource_lineage::jsonb, :properties::jsonb, :created_by_email, :region,
          :state, :flight_id);
        """;
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", resource.getWorkspaceId().toString())
            .addValue("cloud_platform", resource.getResourceType().getCloudPlatform().toString())
            .addValue("resource_id", resource.getResourceId().toString())
            .addValue("name", resource.getName())
            .addValue("description", resource.getDescription())
            .addValue("stewardship_type", resource.getStewardshipType().toSql())
            .addValue("exact_resource_type", resource.getResourceType().toSql())
            .addValue("resource_type", resource.getResourceFamily().toSql())
            .addValue("cloning_instructions", resource.getCloningInstructions().toSql())
            .addValue("attributes", resource.attributesToJson())
            .addValue("resource_lineage", DbSerDes.toJson(resource.getResourceLineage()))
            .addValue("properties", DbSerDes.propertiesToJson(resource.getProperties()))
            // Only set created_by_email and don't need to set created_by_date; that is set by
            // defaultValueComputed
            .addValue("created_by_email", resource.getCreatedByEmail())
            .addValue("state", state.toDb())
            .addValue("flight_id", flightId);
    if (resource.getStewardshipType().equals(CONTROLLED)) {
      ControlledResource controlledResource = resource.castToControlledResource();
      //noinspection deprecation
      params
          .addValue("access_scope", controlledResource.getAccessScope().toSql())
          .addValue("managed_by", controlledResource.getManagedBy().toSql())
          .addValue("associated_app", controlledResource.getApplicationId())
          .addValue("assigned_user", controlledResource.getAssignedUser().orElse(null))
          .addValue(
              "private_resource_state",
              controlledResource
                  .getPrivateResourceState()
                  .map(PrivateResourceState::toSql)
                  .orElse(null))
          .addValue("region", controlledResource.getRegion());
    } else {
      params
          .addValue("access_scope", null)
          .addValue("managed_by", null)
          .addValue("associated_app", null)
          .addValue("assigned_user", null)
          .addValue("private_resource_state", null)
          .addValue("region", null);
    }

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          "Inserted record for resource {} for workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
    } catch (DuplicateKeyException e) {
      throw new DuplicateResourceException(
          String.format(
              "A resource already exists in the workspace that has the same name (%s) or the same id (%s)",
              resource.getName(), resource.getResourceId().toString()));
    }
  }

  @WriteTransaction
  public void updateResourceProperties(
      UUID workspaceUuid, UUID resourceUuid, Map<String, String> properties) {
    if (properties.isEmpty()) {
      throw new MissingRequiredFieldsException("No resource property is specified to update");
    }
    Map<String, String> updatedProperties =
        new HashMap<>(getResourceProperties(workspaceUuid, resourceUuid));
    updatedProperties.putAll(properties);
    storeResourceProperties(updatedProperties, workspaceUuid, resourceUuid);
  }

  @WriteTransaction
  public void deleteResourceProperties(
      UUID workspaceUuid, UUID resourceUuid, List<String> propertyKeys) {
    if (propertyKeys.isEmpty()) {
      throw new MissingRequiredFieldsException("No resource property is specified to delete");
    }
    Map<String, String> properties =
        new HashMap<>(getResourceProperties(workspaceUuid, resourceUuid));
    for (String key : propertyKeys) {
      properties.remove(key);
    }
    storeResourceProperties(properties, workspaceUuid, resourceUuid);
  }

  /** Update the properties column of a given resource in a given workspace. */
  private void storeResourceProperties(
      Map<String, String> properties, UUID workspaceUuid, UUID resourceUuid) {
    final String sql =
        """
          UPDATE resource SET properties = cast(:properties AS jsonb)
          WHERE workspace_id = :workspace_id AND resource_id = :resource_id
        """;

    var params = new MapSqlParameterSource();
    params
        .addValue("properties", DbSerDes.propertiesToJson(properties))
        .addValue("workspace_id", workspaceUuid.toString())
        .addValue("resource_id", resourceUuid.toString());
    jdbcTemplate.update(sql, params);
  }

  private ImmutableMap<String, String> getResourceProperties(
      UUID workspaceUuid, UUID resourceUuid) {
    String selectPropertiesSql =
        """
          SELECT properties FROM resource
          WHERE workspace_id = :workspace_id AND resource_id = :resource_id
        """;
    MapSqlParameterSource propertiesParams =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("resource_id", resourceUuid.toString());
    String result;

    try {
      result = jdbcTemplate.queryForObject(selectPropertiesSql, propertiesParams, String.class);
    } catch (EmptyResultDataAccessException e) {
      throw new ResourceNotFoundException(
          String.format("Cannot find resource %s in workspace %s.", resourceUuid, workspaceUuid));
    }
    return result == null
        ? ImmutableMap.of()
        : ImmutableMap.copyOf(DbSerDes.jsonToProperties(result));
  }

  /**
   * Dispatch by stewardship and resource type to call the correct constructor for the WsmResource
   *
   * @param dbResource Resource data from the database
   * @return WsmResource
   */
  private WsmResource constructResource(DbResource dbResource) {
    Optional<ActivityLogChangeDetails> details =
        workspaceActivityLogDao.getLastUpdatedDetails(
            dbResource.getWorkspaceId(), dbResource.getResourceId().toString());
    dbResource
        .lastUpdatedByEmail(
            details
                .map(ActivityLogChangeDetails::actorEmail)
                .orElse(dbResource.getCreatedByEmail()))
        .lastUpdatedDate(
            details.map(ActivityLogChangeDetails::changeDate).orElse(dbResource.getCreatedDate()));
    WsmResourceHandler handler = dbResource.getResourceType().getResourceHandler();
    return handler.makeResourceFromDb(dbResource);
  }

  private DbResource getDbResourceFromIds(UUID workspaceUuid, UUID resourceId) {
    final String sql = RESOURCE_SELECT_SQL + " AND resource_id = :resource_id";
    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("resource_id", resourceId.toString());
    return getDbResource(sql, params);
  }

  private @Nullable DbResource getDbResource(String sql, MapSqlParameterSource params) {
    try {
      return jdbcTemplate.queryForObject(sql, params, DB_RESOURCE_ROW_MAPPER);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  private DbResource getDbResourceRequired(String sql, MapSqlParameterSource params) {
    DbResource dbResource = getDbResource(sql, params);
    if (dbResource == null) {
      throw new ResourceNotFoundException("Resource not found.");
    }
    return dbResource;
  }
}
