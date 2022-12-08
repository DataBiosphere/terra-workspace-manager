package bio.terra.workspace.db;

import static bio.terra.workspace.service.resource.model.StewardshipType.CONTROLLED;
import static bio.terra.workspace.service.resource.model.StewardshipType.REFERENCED;
import static bio.terra.workspace.service.resource.model.StewardshipType.fromSql;
import static java.util.stream.Collectors.toList;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
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

  /** SQL query for reading all columns from the resource table */
  private static final String RESOURCE_SELECT_SQL =
      """
      SELECT workspace_id, cloud_platform, resource_id, name, description, stewardship_type,
        resource_type, exact_resource_type, cloning_instructions, attributes,
        access_scope, managed_by, associated_app, assigned_user, private_resource_state,
        resource_lineage, properties, created_date, created_by_email, region
      FROM resource WHERE workspace_id = :workspace_id
      """;

  private static final RowMapper<DbResource> DB_RESOURCE_ROW_MAPPER =
      (rs, rowNum) ->
          new DbResource()
              .workspaceUuid(UUID.fromString(rs.getString("workspace_id")))
              .cloudPlatform(CloudPlatform.fromSql(rs.getString("cloud_platform")))
              .resourceId(UUID.fromString(rs.getString("resource_id")))
              .name(rs.getString("name"))
              .description(rs.getString("description"))
              .stewardshipType(fromSql(rs.getString("stewardship_type")))
              .cloudResourceType(WsmResourceFamily.fromSql(rs.getString("resource_type")))
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
              .applicationId(Optional.ofNullable(rs.getString("associated_app")).orElse(null))
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
              .region(rs.getString("region"));

  private final NamedParameterJdbcTemplate jdbcTemplate;

  // -- Common Resource Methods -- //

  @Autowired
  public ResourceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
  public boolean deleteResource(UUID workspaceUuid, UUID resourceId) {
    final String sql =
        "DELETE FROM resource WHERE workspace_id = :workspace_id AND resource_id = :resource_id";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("resource_id", resourceId.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    logger.info(
        "{} record for resource {} in workspace {}",
        (deleted ? "Deleted" : "No Delete - did not find"),
        resourceId,
        workspaceUuid);

    return deleted;
  }

  @WriteTransaction
  public boolean deleteResourceForResourceType(
      UUID workspaceUuid, UUID resourceId, WsmResourceType resourceType) {
    final String sql =
        "DELETE FROM resource WHERE workspace_id = :workspace_id AND resource_id = :resource_id"
            + " AND exact_resource_type = :exact_resource_type";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("resource_id", resourceId.toString())
            .addValue("exact_resource_type", resourceType.toSql());
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
   * enumerateReferences - temporary This is a temporary implementation to support the old
   * DataReference model. It also does not filter by what is visible to the user. I think we will
   * probably change to use a single enumerate across all resources.
   *
   * @param workspaceUuid workspace of interest
   * @param offset paging support
   * @param limit paging support
   * @return list of reference resources
   */
  @ReadTransaction
  public List<ReferencedResource> enumerateReferences(UUID workspaceUuid, int offset, int limit) {
    String sql =
        RESOURCE_SELECT_SQL
            + " AND stewardship_type = :stewardship_type ORDER BY name OFFSET :offset LIMIT :limit";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("stewardship_type", REFERENCED.toSql())
            .addValue("offset", offset)
            .addValue("limit", limit);
    List<DbResource> dbResourceList = jdbcTemplate.query(sql, params, DB_RESOURCE_ROW_MAPPER);

    return dbResourceList.stream()
        .map(this::constructResource)
        .map(ReferencedResource.class::cast)
        .collect(toList());
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
   * Returns a list of all controlled resources in a workspace, optionally filtering by cloud
   * platform.
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

    return constructResource(getDbResource(sql, params));
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

    return constructResource(getDbResource(sql, params));
  }

  // -- Reference Methods -- //

  /**
   * Create a referenced resource row in the database We do creates in flights where the same create
   * is issued more than once.
   *
   * @param resource a filled in referenced resource
   * @throws DuplicateResourceException on a duplicate resource_id or (workspace_id, name)
   */
  @WriteTransaction
  public void createReferencedResource(WsmResource resource) throws DuplicateResourceException {
    if (resource.getStewardshipType() != REFERENCED) {
      throw new InternalLogicException("Expected a referenced resource");
    }
    storeResource(resource);
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
    StringBuilder sb = new StringBuilder("UPDATE resource SET ");

    sb.append(DbUtils.setColumnsClause(params, "attributes"));

    sb.append(" WHERE workspace_id = :workspace_id AND resource_id = :resource_id");

    params
        .addValue("workspace_id", workspaceUuid.toString())
        .addValue("resource_id", resourceId.toString());

    int rowsAffected = jdbcTemplate.update(sb.toString(), params);
    boolean updated = rowsAffected > 0;

    logger.info(
        "{} record for resource {} in workspace {}",
        (updated ? "Updated" : "No Update - did not find"),
        resourceId,
        workspaceUuid);

    return updated;
  }

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
   * Update name, description, and/or cloning instructions of the resource.
   *
   * @param name name of the resource, may be null if it does not need to be updated
   * @param description description of the resource, may be null if it does not need to be updated
   */
  @WriteTransaction
  public boolean updateResource(
      UUID workspaceUuid,
      UUID resourceId,
      @Nullable String name,
      @Nullable String description,
      @Nullable CloningInstructions cloningInstructions) {
    return updateResourceWorker(
        workspaceUuid, resourceId, name, description, /*attributes=*/ null, cloningInstructions);
  }

  /**
   * Create a controlled resource in the database
   *
   * @param controlledResource controlled resource to create
   * @throws DuplicateResourceException on a duplicate resource_id or (workspace_id, name)
   */
  @WriteTransaction
  public void createControlledResource(ControlledResource controlledResource)
      throws DuplicateResourceException {

    if (!cloudContextExists(
        controlledResource.getWorkspaceId(),
        controlledResource.getResourceType().getCloudPlatform())) {
      throw new CloudContextRequiredException(
          "No cloud context found in which to create a controlled resource");
    }

    verifyUniqueness(controlledResource);
    storeResource(controlledResource);
  }

  private boolean cloudContextExists(UUID workspaceUuid, CloudPlatform cloudPlatform) {
    // Check existence of the cloud context for this workspace
    final String sql =
        "SELECT COUNT(*) FROM cloud_context"
            + " WHERE workspace_id = :workspace_id AND cloud_platform = :cloud_platform";

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
  private void verifyUniqueness(ControlledResource controlledResource) {
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
   * #setPrivateResourcesStateForWorkspaceUser(UUID, String, PrivateResourceState)}
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
      UUID workspaceUuid, String userEmail, PrivateResourceState state) {
    final String sql =
        """
          UPDATE resource SET private_resource_state = :private_resource_state
          WHERE workspace_id = :workspace_id AND assigned_user = :user_email
         """;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("private_resource_state", state.toSql())
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("user_email", userEmail);
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

  private void storeResource(WsmResource resource) {

    // TODO: add resource locking to fix this
    //  We create resources in flights, so we have steps that call resource creation that may
    //  get run more than once. The safe solution is to "lock" the resource by writing the flight id
    //  into the row at creation. Then it is possible on a re-insert to know whether the error is
    //  because this flight step is re-running or because some other flight used the same resource
    //  id. The small risk we have here is that a duplicate resource id of will appear to be
    //  successfully created, but in fact will be silently rejected.

    final String countSql = "SELECT COUNT(*) FROM resource WHERE resource_id = :resource_id";
    MapSqlParameterSource countParams =
        new MapSqlParameterSource().addValue("resource_id", resource.getResourceId().toString());
    Integer count = jdbcTemplate.queryForObject(countSql, countParams, Integer.class);
    if (count != null && count == 1) {
      return;
    }

    final String sql =
        """
        INSERT INTO resource (workspace_id, cloud_platform, resource_id, name, description,
          stewardship_type, exact_resource_type, resource_type, cloning_instructions, attributes,
          access_scope, managed_by, associated_app, assigned_user, private_resource_state,
          resource_lineage, properties, created_by_email, region)
        VALUES (:workspace_id, :cloud_platform, :resource_id, :name, :description,
          :stewardship_type, :exact_resource_type, :resource_type, :cloning_instructions,
          cast(:attributes AS jsonb), :access_scope, :managed_by, :associated_app, :assigned_user,
          :private_resource_state, :resource_lineage::jsonb, :properties::jsonb, :created_by_email, :region);
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
            .addValue("created_by_email", resource.getCreatedByEmail());
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
          .addValue("private_resource_state", null);
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
    WsmResourceHandler handler = dbResource.getResourceType().getResourceHandler();
    return handler.makeResourceFromDb(dbResource);
  }

  private DbResource getDbResource(String sql, MapSqlParameterSource params) {
    try {
      return jdbcTemplate.queryForObject(sql, params, DB_RESOURCE_ROW_MAPPER);
    } catch (EmptyResultDataAccessException e) {
      throw new ResourceNotFoundException("Resource not found.");
    }
  }
}
