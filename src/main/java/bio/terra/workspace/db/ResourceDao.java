package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.db.exception.InvalidDaoRequestException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.ControlledAccessType;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.reference.ReferenceGcsBucketResource;
import bio.terra.workspace.service.resource.reference.ReferenceResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static bio.terra.workspace.service.resource.model.StewardshipType.CONTROLLED;
import static bio.terra.workspace.service.resource.model.StewardshipType.REFERENCE;
import static bio.terra.workspace.service.resource.model.StewardshipType.fromSql;

@Component
public class ResourceDao {
  private static final Logger logger = LoggerFactory.getLogger(ResourceDao.class);

  /** SQL query for reading all columns from the resource table */
  private static final String RESOURCE_SELECT_SQL =
      "SELECT workspace_id, cloud_platform, resource_id, name, description, "
          + "stewardship_type, resource_type, cloning_instructions, attributes,"
          + " access, associated_app, assigned_user"
          + " FROM resource WHERE workspace_id = :workspace_id ";

  private static final RowMapper<DbResource> DB_RESOURCE_ROW_MAPPER =
      (rs, rowNum) -> {
        return new DbResource()
            .workspaceId(UUID.fromString(rs.getString("workspace_id")))
            .cloudPlatform(CloudPlatform.fromSql(rs.getString("cloud_platform")))
            .resourceId(UUID.fromString(rs.getString("resource_id")))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .stewardshipType(fromSql(rs.getString("stewardship_type")))
            .resourceType(WsmResourceType.fromSql(rs.getString("resource_type")))
            .cloningInstructions(CloningInstructions.fromSql(rs.getString("cloning_instructions")))
            .attributes(rs.getString("attributes"))
            .accessType(ControlledAccessType.fromSql(rs.getString("access")))
            .associatedApp(UUID.fromString(rs.getString("associated_app")))
            .assignedUser(rs.getString("assigned_user"));
      };

  private final NamedParameterJdbcTemplate jdbcTemplate;

  // -- Common Resource Methods -- //

  @Autowired
  public ResourceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean deleteResource(UUID workspaceId, UUID resourceId) {
    final String sql =
        "DELETE FROM resource WHERE workspace_id = :workspace_id AND resource_id = :resource_id";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("resource_id", resourceId.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    logger.info(
        "{} record for resource {} in workspace {}",
        (deleted ? "Deleted" : "No Delete - did not find"),
        resourceId,
        workspaceId);

    return deleted;
  }

  /**
   * enumerateReferences - temporary This is a temporary implementation to support the old
   * DataReference model. It also does not filter by what is visible to the user. I think we will
   * probably change to use a single enumerate across all resources.
   *
   * @param workspaceId workspace of interest
   * @param offset paging support
   * @param limit paging support
   * @return list of reference resources
   */
  public List<ReferenceResource> enumerateReferences(UUID workspaceId, int offset, int limit) {
    String sql =
        RESOURCE_SELECT_SQL
            + " AND stewardship_type = 'REFERENCE' ORDER BY name OFFSET :offset LIMIT :limit";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("offset", offset)
            .addValue("limit", limit);
    List<DbResource> dbResourceList = jdbcTemplate.query(sql, params, DB_RESOURCE_ROW_MAPPER);

    return dbResourceList.stream()
        .map(this::constructResource)
        .map(ReferenceResource.class::cast)
        .collect(Collectors.toList());
  }

  /**
   * Retrieve a resource by ID
   *
   * @param workspaceId identifier of workspace for the lookup
   * @param resourceId identifer of the resource for the lookup
   * @return WsmResource object
   */
  @Transactional(
      propagation = Propagation.REQUIRED,
      isolation = Isolation.SERIALIZABLE,
      readOnly = true)
  public WsmResource getResource(UUID workspaceId, UUID resourceId) {
    return getResourceWithId(workspaceId, resourceId);
  }

  /**
   * Retrieve a data reference by name. Names are unique per workspace.
   *
   * @param workspaceId identifier of workspace for the lookup
   * @param name name of the resource
   * @return WsmResource object
   */
  @Transactional(
      propagation = Propagation.REQUIRED,
      isolation = Isolation.SERIALIZABLE,
      readOnly = true)
  public WsmResource getResourceByName(UUID workspaceId, String name) {
    return getResourceWithName(workspaceId, name);
  }

  // -- Reference Methods -- //

  /**
   * Create a reference in the database
   *
   * @param resource a filled in reference resource
   * @throws DuplicateDataReferenceException on a duplicate resource_id or (workspace_id, name)
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createReferenceResource(ReferenceResource resource)
      throws DuplicateDataReferenceException {
    storeResource(
        resource.getWorkspaceId(),
        resource.getResourceId(),
        resource.getName(),
        resource.getDescription(),
        REFERENCE,
        resource.getResourceType(),
        resource.getCloningInstructions(),
        resource.getJsonAttributes(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  // -- Controlled Resource Methods -- //

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean updateReferenceResource(
      UUID workspaceId, UUID referenceId, String name, String description) {
    if (name == null && description == null) {
      throw new InvalidDaoRequestException("Must specify name or description to update.");
    }

    var params = new MapSqlParameterSource();

    if (name != null) {
      params.addValue("name", name);
    }

    if (description != null) {
      params.addValue("description", description);
    }

    return updateResource(workspaceId, referenceId, params);
  }

  // -- Private Methods -- //

  /**
   * Create a controlled resource in the database
   *
   * @param controlledResource controlled resource to create
   * @throws DuplicateDataReferenceException on a duplicate resource_id or (workspace_id, name)
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createControlledResource(ControlledResource controlledResource)
      throws DuplicateDataReferenceException {

    storeResource(
        controlledResource.getWorkspaceId(),
        controlledResource.getResourceId(),
        controlledResource.getName(),
        controlledResource.getDescription(),
        CONTROLLED,
        controlledResource.getResourceType(),
        controlledResource.getCloningInstructions(),
        controlledResource.getJsonAttributes(),
        // TODO: add this to ControlledResource
        Optional.of(ControlledAccessType.USER_SHARED),
        // TODO: add associated app to ControlledResource
        Optional.empty(),
        // TODO: rename this
        controlledResource.getAssignedUser());
  }

  private void storeResource(
      UUID workspaceId,
      UUID resourceId,
      String name,
      String description,
      StewardshipType stewardshipType,
      WsmResourceType resourceType,
      CloningInstructions cloningInstructions,
      String attributes,
      Optional<ControlledAccessType> accessType,
      Optional<UUID> associatedApp,
      Optional<String> assignedUser) {
    final String sql =
        "INSERT resource (workspace_id, cloud_type, resource_id, name, description, stewardship_type,"
            + " resource_type, cloning_instructions, attributes, access, associated_app, assigned_user)"
            + " VALUES (:workspace_id, :cloud_type, :resource_id, :name, :description, :stewardship_type,"
            + " :resource_type, :cloning_instructions, cast(:attributes AS json),"
            + " :access, :associated_app, :assigned_user)";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", resourceType.getCloudPlatform().toString())
            .addValue("resource_id", resourceId.toString())
            .addValue("name", name)
            .addValue("description", description)
            .addValue("stewardship_type", stewardshipType.toSql())
            .addValue("resource_type", resourceType.toSql())
            .addValue("cloning_instructions", cloningInstructions.toSql())
            .addValue("attributes", attributes)
            .addValue("access", accessType.map(ControlledAccessType::toSql).orElse(null))
            .addValue("associated_app", associatedApp.map(UUID::toString).orElse(null))
            .addValue("assigned_user", assignedUser.orElse(null));

    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for resource {} for workspace {}", resourceId, workspaceId);
    } catch (DuplicateKeyException e) {
      throw new DuplicateDataReferenceException(
          "A resource named " + name + " already exists in the workspace");
    }
  }

  private WsmResource getResourceWithId(UUID workspaceId, UUID resourceId) {
    final String sql = RESOURCE_SELECT_SQL + " AND resource_id = :resource_id";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("resource_id", resourceId.toString());

    return constructResource(getDbResource(sql, params));
  }

  private WsmResource getResourceWithName(UUID workspaceId, String name) {
    final String sql = RESOURCE_SELECT_SQL + " AND name = :name";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("name", name);

    return constructResource(getDbResource(sql, params));
  }

  /**
   * Dispatch by stewardship and resource type to call the correct constructor for the WsmResource
   *
   * @param dbResource Resource data from the database
   * @return WsmResource
   */
  private WsmResource constructResource(DbResource dbResource) {
    switch (dbResource.getStewardshipType()) {
      case REFERENCE:
        switch (dbResource.getResourceType()) {
          case GCS_BUCKET:
            return new ReferenceGcsBucketResource(dbResource);

          case BIG_QUERY_DATASET:
          case DATA_REPO_SNAPSHOT:
          default:
            throw new InvalidMetadataException(
                "Invalid reference resource type" + dbResource.getResourceType().toString());
        }

      case CONTROLLED:
        switch (dbResource.getResourceType()) {
          case GCS_BUCKET:
            break;

          default:
            throw new InvalidMetadataException(
                "Invalid controlled resource type" + dbResource.getResourceType().toString());
        }

      case MONITORED:
      default:
        throw new InvalidMetadataException(
            "Invalid stewardship type" + dbResource.getStewardshipType().toString());
    }
  }

  private DbResource getDbResource(String sql, MapSqlParameterSource params) {
    try {
      return jdbcTemplate.queryForObject(sql, params, DB_RESOURCE_ROW_MAPPER);
    } catch (EmptyResultDataAccessException e) {
      throw new ResourceNotFoundException("Resource not found.");
    }
  }

  /**
   * This is an open ended method for constructing the SQL update statement. To use it, build the
   * parameter list making the param name equal to the column name you want to update. The method
   * generates the column_name = :column_name list. It is an error if the params map is empty.
   *
   * @param params sql parameters
   * @param workspaceId workspace identifier - not strictly necessarily, but an extra validation
   * @param resourceId resource identifier
   */
  private boolean updateResource(UUID workspaceId, UUID resourceId, MapSqlParameterSource params) {
    StringBuilder sb = new StringBuilder("UPDATE resource SET ");

    String[] parameterNames = params.getParameterNames();
    if (parameterNames.length == 0) {
      throw new InvalidDaoRequestException("Must specify some data to be updated.");
    }
    for (int i = 0; i < parameterNames.length; i++) {
      String columnName = parameterNames[i];
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(columnName).append(" = :").append(columnName);
    }
    sb.append(" WHERE workspace_id = :workspace_id AND resource_id = :resource_id");

    params
        .addValue("workspace_id", workspaceId.toString())
        .addValue("resource_id", resourceId.toString());

    int rowsAffected = jdbcTemplate.update(sb.toString(), params);
    boolean updated = rowsAffected > 0;

    logger.info(
        "{} record for resource {} in workspace {}",
        (updated ? "Updated" : "No Update - did not find"),
        resourceId,
        workspaceId);

    return updated;
  }
}
