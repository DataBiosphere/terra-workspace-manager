package bio.terra.workspace.db;

import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.db.exception.InvalidDaoRequestException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.resource.StewardshipType;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.ControlledAccessType;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ResourceDao {
  private static final Logger logger = LoggerFactory.getLogger(ResourceDao.class);

  /** SQL query for reading all columns from the resource table */
  private static final String resourceSelectSql =
      "SELECT workspace_id, cloud_platform, resource_id, name, description, "
          + "stewardship_type, resource_type, cloning_instructions, attributes,"
          + " access, associated_app, assigned_user"
          + " FROM resource WHERE workspace_id = :workspace_id ";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public ResourceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // -- Common Resource Methods -- //

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

  // -- Reference Methods -- //

  /**
   * Create a reference in the database
   *
   * @param request data reference request
   * @param resourceId resourceId to use for the resource
   * @throws DuplicateDataReferenceException on a duplicate resource_id or (workspace_id, name)
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createDataReference(DataReferenceRequest request, UUID resourceId)
      throws DuplicateDataReferenceException {
    storeResource(
        request.workspaceId(),
        resourceId,
        request.name(),
        request.description(),
        StewardshipType.REFERENCE,
        request.referenceType(),
        request.cloningInstructions(),
        request.referenceObject().toJson());
  }

  /**
   * Retrieve a data reference by ID
   * @param workspaceId identifier of workspace for the lookup
   * @param resourceId identifer of the resource for the lookup
   * @return DataReference object
   */
  @Transactional(
      propagation = Propagation.REQUIRED,
      isolation = Isolation.SERIALIZABLE,
      readOnly = true)
  public DataReference getDataReference(UUID workspaceId, UUID resourceId) {
    DbResource dbResource = getResourceById(workspaceId, resourceId);

    return DataReference.builder()
        .workspaceId(dbResource.getWorkspaceId())
        .referenceId(dbResource.getResourceId())
        .name(dbResource.getName().orElse(null))
        .description(dbResource.getDescription().orElse(null))
        .referenceType(dbResource.getResourceType())
        .cloningInstructions(dbResource.getCloningInstructions())
        .referenceObject(ReferenceObject.fromJson(dbResource.getAttributes()))
        .build();
  }

  /**
   * Retrieve a data reference by name. Names are unique per workspace.
   * @param workspaceId identifier of workspace for the lookup
   * @param name name of the resource
   * @return DataReference object
   */
  @Transactional(
      propagation = Propagation.REQUIRED,
      isolation = Isolation.SERIALIZABLE,
      readOnly = true)
  public DataReference getDataReferenceByName(UUID workspaceId, String name) {
    DbResource dbResource = getResourceByName(workspaceId, name);
    return DataReference.builder()
        .workspaceId(dbResource.getWorkspaceId())
        .referenceId(dbResource.getResourceId())
        .name(dbResource.getName().orElse(null))
        .description(dbResource.getDescription().orElse(null))
        .referenceType(dbResource.getResourceType())
        .cloningInstructions(dbResource.getCloningInstructions())
        .referenceObject(ReferenceObject.fromJson(dbResource.getAttributes()))
        .build();
  }

  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public boolean updateDataReference(UUID workspaceId, UUID referenceId, String name, String description) {
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

  // -- Controlled Resource Methods -- //

  /**
   * Create a controlled resource in the database
   *
   * @param controlledResource controlled resource to create
   * @param resourceId resourceId to use for the resource
   * @throws DuplicateDataReferenceException on a duplicate resource_id or (workspace_id, name)
   */
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
  public void createControlledResource(ControlledResource controlledResource, UUID resourceId)
      throws DuplicateDataReferenceException {

    storeResource(
        controlledResource.getWorkspaceId(),
        resourceId,
        controlledResource.getName(),
        controlledResource.getDescription(),
        StewardshipType.CONTROLLED,
        controlledResource.getResourceType(),
        controlledResource.getCloningInstructions(),
        controlledResource.getJsonAttributes());

    // Store the extra stuff for controlled resources
    final String sql =
        "INSERT INTO controlled_resource "
            + "(resource_id, access, associated_app, assigned_user) "
            + " VALUES (:resourceId, :access, :associated_app, :assigned_user)";

    // TODO: the access type needs to be plumbed through from the REST API
    // TODO: we will also need to plumb in associated app when we implement applications
    final var params =
        new MapSqlParameterSource()
            .addValue("resourceId", resourceId)
            .addValue("access", ControlledAccessType.USER_SHARED.toSql())
            .addValue("associated_app", null)
            .addValue("assigned_user", controlledResource.getOwner().orElse(null));

    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted controlled_resource record for resource {}", resourceId);
    } catch (DuplicateKeyException e) {
      // This should not be possible. Would have failed on the resource table
      throw new InternalLogicException(
          String.format("Resource %s already exists in the controlled_resource table", resourceId),
          e);
    }
  }

  // -- Private Methods -- //

  private void storeResource(
      UUID workspaceId,
      UUID resourceId,
      String name,
      String description,
      StewardshipType stewardshipType,
      WsmResourceType resourceType,
      CloningInstructions cloningInstructions,
      String attributes) {
    final String sql =
        "INSERT resource (workspace_id, cloud_type, resource_id, name, description, stewardship_type,"
            + " resource_type, cloning_instructions, attributes) VALUES "
            + "(:workspace_id, :cloud_type, :resource_id, :name, :description, :stewardship_type,"
            + " :resource_type, :cloning_instructions, cast(:attributes AS json))";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_type", resourceType.getCloudPlatform().toString())
            .addValue("resource_id", resourceId.toString())
            .addValue("name", name)
            .addValue("description", description)
            .addValue("resource_type", resourceType.toSql())
            .addValue("cloning_instructions", cloningInstructions.toSql())
            .addValue("attributes", attributes);

    try {
      jdbcTemplate.update(sql, params);
      logger.info("Inserted record for resource {} for workspace {}", resourceId, workspaceId);
    } catch (DuplicateKeyException e) {
      throw new DuplicateDataReferenceException(
          "A resource named " + name + " already exists in the workspace");
    }
  }

  private DbResource getResourceById(UUID workspaceId, UUID resourceId) {
    final String sql = resourceSelectSql + " AND resource_id = :resource_id";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("resource_id", resourceId.toString());

    return getResource(sql, params);
  }

  private DbResource getResourceByName(UUID workspaceId, String name) {
    final String sql = resourceSelectSql + " AND name = :name";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("name", name);

    return getResource(sql, params);
  }

  private DbResource getResource(String sql, MapSqlParameterSource params) {
    try {
      return jdbcTemplate.queryForObject(
          sql,
          params,
          (rs, rowNum) -> {
            return new DbResource()
                .workspaceId(UUID.fromString(rs.getString("workspace_id")))
                .cloudPlatform(CloudPlatform.fromSql(rs.getString("cloud_platform")))
                .resourceId(UUID.fromString(rs.getString("resource_id")))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .stewardshipType(StewardshipType.fromSql(rs.getString("stewardship_type")))
                .resourceType(WsmResourceType.fromSql(rs.getString("resource_type")))
                .cloningInstructions(
                    CloningInstructions.fromSql(rs.getString("cloning_instructions")))
                .attributes(rs.getString("attributes"))
                .accessType(ControlledAccessType.fromSql(rs.getString("access")))
                .associatedApp(UUID.fromString(rs.getString("associated_app")))
                .assignedUser(rs.getString("assigned_user"));
          });
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
