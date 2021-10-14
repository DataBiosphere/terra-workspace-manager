package bio.terra.workspace.db;

import static bio.terra.workspace.service.resource.model.StewardshipType.CONTROLLED;
import static bio.terra.workspace.service.resource.model.StewardshipType.REFERENCED;
import static bio.terra.workspace.service.resource.model.StewardshipType.fromSql;
import static java.util.stream.Collectors.toList;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.*;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.ReferencedResource;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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
      "SELECT workspace_id, cloud_platform, resource_id, name, description, "
          + "stewardship_type, resource_type, cloning_instructions, attributes,"
          + " access_scope, managed_by, associated_app, assigned_user"
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
            .accessScope(
                Optional.ofNullable(rs.getString("access_scope"))
                    .map(AccessScopeType::fromSql)
                    .orElse(null))
            .managedBy(
                Optional.ofNullable(rs.getString("managed_by"))
                    .map(ManagedByType::fromSql)
                    .orElse(null))
            .associatedApp(
                Optional.ofNullable(rs.getString("associated_app"))
                    .map(UUID::fromString)
                    .orElse(null))
            .assignedUser(rs.getString("assigned_user"));
      };

  private final NamedParameterJdbcTemplate jdbcTemplate;

  // -- Common Resource Methods -- //

  @Autowired
  public ResourceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
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
  @ReadTransaction
  public List<ReferencedResource> enumerateReferences(UUID workspaceId, int offset, int limit) {
    String sql =
        RESOURCE_SELECT_SQL
            + " AND stewardship_type = :stewardship_type ORDER BY name OFFSET :offset LIMIT :limit";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
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
   * @param workspaceId identifier for work space to enumerate
   * @param resourceType filter by this resource type - optional
   * @param stewardshipType filtered by this stewardship type - optional
   * @param offset starting row for result
   * @param limit maximum number of rows to return
   * @return list of resources
   */
  @ReadTransaction
  public List<WsmResource> enumerateResources(
      UUID workspaceId,
      @Nullable WsmResourceType resourceType,
      @Nullable StewardshipType stewardshipType,
      int offset,
      int limit) {

    // We supply the toSql() forms of the stewardship values as parameters, so that string is only
    // defined in one place. We do not always use the stewardship values, but there is no harm
    // in having extra params.
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("offset", offset)
            .addValue("limit", limit)
            .addValue("referenced_resource", REFERENCED.toSql())
            .addValue("controlled_resource", CONTROLLED.toSql());

    StringBuilder sb = new StringBuilder(RESOURCE_SELECT_SQL);
    if (resourceType != null) {
      sb.append(" AND resource_type = :resource_type");
      params.addValue("resource_type", resourceType.toSql());
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
   * @param workspaceId ID of the workspace to return resources from.
   * @param cloudPlatform Optional. If present, this will only return resources from the specified
   *     cloud platform. If null, this will return resources from all cloud platforms.
   */
  @ReadTransaction
  public List<ControlledResource> listControlledResources(
      UUID workspaceId, @Nullable CloudPlatform cloudPlatform) {
    String sql = RESOURCE_SELECT_SQL + " AND stewardship_type = :controlled_resource ";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
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

  /** Returns a list of all private controlled resources assigned to a user in a given workspace. */
  @ReadTransaction
  public List<ControlledResource> listPrivateResourcesByUser(UUID workspaceId, String userEmail) {
    String sql =
        RESOURCE_SELECT_SQL
            + " AND stewardship_type = :controlled_resource"
            + " AND access_scope = :access_scope"
            + " AND assigned_user = :user_email";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("controlled_resource", CONTROLLED.toSql())
            .addValue("access_scope", AccessScopeType.ACCESS_SCOPE_PRIVATE.toSql())
            .addValue("user_email", userEmail);

    List<DbResource> dbResources = jdbcTemplate.query(sql, params, DB_RESOURCE_ROW_MAPPER);
    return dbResources.stream()
        .map(this::constructResource)
        .map(WsmResource::castToControlledResource)
        .collect(Collectors.toList());
  }

  /**
   * Deletes all controlled resources on a specified cloud platform in a workspace.
   *
   * @param workspaceId ID of the workspace to return resources from.
   * @param cloudPlatform The cloud platform to delete resources from. Unlike
   *     listControlledResources, this is not optional.
   * @return True if at least one resource was deleted, false if no resources were deleted.
   */
  @WriteTransaction
  public boolean deleteAllControlledResources(UUID workspaceId, CloudPlatform cloudPlatform) {
    String sql =
        "DELETE FROM resource WHERE workspace_id = :workspace_id AND cloud_platform = :cloud_platform AND stewardship_type = :controlled_resource";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("cloud_platform", cloudPlatform.toSql())
            .addValue("controlled_resource", CONTROLLED.toSql());
    int rowsDeleted = jdbcTemplate.update(sql, params);
    return rowsDeleted > 0;
  }

  /**
   * Retrieve a resource by ID
   *
   * @param workspaceId identifier of workspace for the lookup
   * @param resourceId identifier of the resource for the lookup
   * @return WsmResource object
   */
  @ReadTransaction
  public WsmResource getResource(UUID workspaceId, UUID resourceId) {
    final String sql = RESOURCE_SELECT_SQL + " AND resource_id = :resource_id";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("resource_id", resourceId.toString());

    return constructResource(getDbResource(sql, params));
  }

  /**
   * Retrieve a data reference by name. Names are unique per workspace.
   *
   * @param workspaceId identifier of workspace for the lookup
   * @param name name of the resource
   * @return WsmResource object
   */
  @ReadTransaction
  public WsmResource getResourceByName(UUID workspaceId, String name) {
    final String sql = RESOURCE_SELECT_SQL + " AND name = :name";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("name", name);

    return constructResource(getDbResource(sql, params));
  }

  // -- Reference Methods -- //

  /**
   * Create a reference in the database We do creates in flights where the same create is issues
   * more than once.
   *
   * @param resource a filled in reference resource
   * @throws DuplicateResourceException on a duplicate resource_id or (workspace_id, name)
   */
  @WriteTransaction
  public void createReferenceResource(ReferencedResource resource)
      throws DuplicateResourceException {
    storeResource(resource);
  }

  @WriteTransaction
  public boolean updateResource(
      UUID workspaceId, UUID resourceId, String name, String description) {
    if (name == null && description == null) {
      return false;
    }

    var params = new MapSqlParameterSource();

    if (name != null) {
      params.addValue("name", name);
    }

    if (description != null) {
      params.addValue("description", description);
    }

    return updateResourceColumns(workspaceId, resourceId, params);
  }

  /**
   * This is an open ended method for constructing the SQL update statement. To use it, build the
   * parameter list making the param name equal to the column name you want to update. The method
   * generates the column_name = :column_name list. It is an error if the params map is empty.
   *
   * @param columnParams sql parameters
   * @param workspaceId workspace identifier - not strictly necessarily, but an extra validation
   * @param resourceId resource identifier
   */
  private boolean updateResourceColumns(
      UUID workspaceId, UUID resourceId, MapSqlParameterSource columnParams) {
    StringBuilder sb = new StringBuilder("UPDATE resource SET ");

    sb.append(DbUtils.setColumnsClause(columnParams));
    sb.append(" WHERE workspace_id = :workspace_id AND resource_id = :resource_id");

    MapSqlParameterSource queryParams = new MapSqlParameterSource();
    queryParams
        .addValues(columnParams.getValues())
        .addValue("workspace_id", workspaceId.toString())
        .addValue("resource_id", resourceId.toString());

    int rowsAffected = jdbcTemplate.update(sb.toString(), queryParams);
    boolean updated = rowsAffected > 0;

    logger.info(
        "{} record for resource {} in workspace {}",
        (updated ? "Updated" : "No Update - did not find"),
        resourceId,
        workspaceId);

    return updated;
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

    // Make sure there is a valid cloud context before we create the controlled resource
    final String sql =
        "SELECT COUNT(*) FROM cloud_context"
            + " WHERE workspace_id = :workspace_id AND cloud_platform = :cloud_platform";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", controlledResource.getWorkspaceId().toString())
            .addValue(
                "cloud_platform", controlledResource.getResourceType().getCloudPlatform().toSql());
    Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    if (count == null || count == 0) {
      throw new CloudContextRequiredException(
          "No cloud context found in which to create a controlled resource");
    }

    // Validate that the resource to be created doesn't already exist according to per-resource type
    // uniqueness rules. This prevents a race condition allowing a new resource to point to the same
    // cloud artifact as another, even if it has a different resource name and ID.
    switch (controlledResource.getResourceType()) {
      case GCS_BUCKET:
        validateUniqueGcsBucket(controlledResource.castToGcsBucketResource());
        break;
      case AI_NOTEBOOK_INSTANCE:
        validateUniqueAiNotebookInstance(controlledResource.castToAiNotebookInstanceResource());
        break;
      case BIG_QUERY_DATASET:
        validateUniqueBigQueryDataset(controlledResource.castToBigQueryDatasetResource());
        break;
      case DATA_REPO_SNAPSHOT:
      case AZURE_IP:
        validateUniqueAzureIp(controlledResource.castToAzureIpResource());
        break;
      case AZURE_DISK:
        validateUniqueAzureDisk(controlledResource.castToAzureDiskResource());
        break;
      default:
        throw new IllegalArgumentException(
            String.format(
                "Resource type %s not supported", controlledResource.getResourceType().toString()));
    }

    storeResource(controlledResource);
  }

  private void validateUniqueGcsBucket(ControlledGcsBucketResource bucketResource) {
    String bucketSql =
        "SELECT COUNT(1)"
            + " FROM resource"
            + " WHERE resource_type = :resource_type"
            + " AND attributes->>'bucketName' = :bucket_name";
    MapSqlParameterSource bucketParams =
        new MapSqlParameterSource()
            .addValue("bucket_name", bucketResource.getBucketName())
            .addValue("resource_type", WsmResourceType.GCS_BUCKET.toSql());
    Integer matchingBucketCount =
        jdbcTemplate.queryForObject(bucketSql, bucketParams, Integer.class);
    if (matchingBucketCount != null && matchingBucketCount > 0) {
      throw new DuplicateResourceException(
          String.format(
              "A GCS bucket resource named %s already exists", bucketResource.getBucketName()));
    }
  }

  private void validateUniqueAiNotebookInstance(
      ControlledAiNotebookInstanceResource notebookResource) {
    // Workspace ID is a proxy for project ID, which works because there is a permanent, 1:1
    // correspondence between workspaces and GCP projects.
    String sql =
        "SELECT COUNT(1)"
            + " FROM resource"
            + " WHERE resource_type = :resource_type"
            + " AND workspace_id = :workspace_id"
            + " AND attributes->>'instanceId' = :instance_id"
            + " AND attributes->>'location' = :location";
    MapSqlParameterSource sqlParams =
        new MapSqlParameterSource()
            .addValue("resource_type", WsmResourceType.AI_NOTEBOOK_INSTANCE.toSql())
            .addValue("workspace_id", notebookResource.getWorkspaceId().toString())
            .addValue("instance_id", notebookResource.getInstanceId())
            .addValue("location", notebookResource.getLocation());
    Integer matchingCount = jdbcTemplate.queryForObject(sql, sqlParams, Integer.class);
    if (matchingCount != null && matchingCount > 0) {
      throw new DuplicateResourceException(
          String.format(
              "An AI Notebook instance with ID %s already exists",
              notebookResource.getInstanceId()));
    }
  }

  private void validateUniqueBigQueryDataset(ControlledBigQueryDatasetResource datasetResource) {
    // Workspace ID is a proxy for project ID, which works because there is a permanent, 1:1
    // correspondence between workspaces and GCP projects.
    String sql =
        "SELECT COUNT(1)"
            + " FROM resource"
            + " WHERE resource_type = :resource_type"
            + " AND workspace_id = :workspace_id"
            + " AND attributes->>'datasetName' = :dataset_name";
    MapSqlParameterSource sqlParams =
        new MapSqlParameterSource()
            .addValue("resource_type", WsmResourceType.BIG_QUERY_DATASET.toSql())
            .addValue("workspace_id", datasetResource.getWorkspaceId().toString())
            .addValue("dataset_name", datasetResource.getDatasetName());
    Integer matchingCount = jdbcTemplate.queryForObject(sql, sqlParams, Integer.class);
    if (matchingCount != null && matchingCount > 0) {
      throw new DuplicateResourceException(
          String.format(
              "A BigQuery dataset with ID %s already exists", datasetResource.getDatasetName()));
    }
  }

  private void validateUniqueAzureIp(ControlledAzureIpResource ipResource) {
    String sql =
        "SELECT COUNT(1)"
            + " FROM resource"
            + " WHERE resource_type = :resource_type"
            + " AND workspace_id = :workspace_id"
            + " AND attributes->>'ipName' = :ip_name";
    MapSqlParameterSource sqlParams =
        new MapSqlParameterSource()
            .addValue("resource_type", WsmResourceType.AZURE_IP.toSql())
            .addValue("workspace_id", ipResource.getWorkspaceId().toString())
            .addValue("ip_name", ipResource.getIpName());
    Integer matchingCount = jdbcTemplate.queryForObject(sql, sqlParams, Integer.class);
    if (matchingCount != null && matchingCount > 0) {
      throw new DuplicateResourceException(
          String.format("An Azure IP with ID %s already exists", ipResource.getIpName()));
    }
  }

  private void validateUniqueAzureDisk(ControlledAzureDiskResource resource) {
    String sql =
        "SELECT COUNT(1)"
            + " FROM resource"
            + " WHERE resource_type = :resource_type"
            + " AND workspace_id = :workspace_id"
            + " AND attributes->>'diskName' = :disk_name";
    MapSqlParameterSource sqlParams =
        new MapSqlParameterSource()
            .addValue("resource_type", WsmResourceType.AZURE_DISK.toSql())
            .addValue("workspace_id", resource.getWorkspaceId().toString())
            .addValue("disk_name", resource.getDiskName());
    Integer matchingCount = jdbcTemplate.queryForObject(sql, sqlParams, Integer.class);
    if (matchingCount != null && matchingCount > 0) {
      throw new DuplicateResourceException(
          String.format("An Azure IP with ID %s already exists", resource.getDiskName()));
    }
  }

  private void storeResource(WsmResource resource) {

    // TODO: add resource locking to fix this
    //  We create resources in flights, so we have steps that call resource creation that may
    //  get run more than once. The safe solution is to "lock" the resource by writing the flight id
    //  into the row at creation. Then it is possible on a re-insert to know whether the error is
    //  because this flight step is re-running or because some other flight used the same resource
    // id.
    //  The small risk we have here is that a duplicate resource id of will appear to be
    // successfully
    //  created, but in fact will be silently rejected.

    final String countSql = "SELECT COUNT(*) FROM resource WHERE resource_id = :resource_id";
    MapSqlParameterSource countParams =
        new MapSqlParameterSource().addValue("resource_id", resource.getResourceId().toString());
    Integer count = jdbcTemplate.queryForObject(countSql, countParams, Integer.class);
    if (count != null && count == 1) {
      return;
    }

    final String sql =
        "INSERT INTO resource (workspace_id, cloud_platform, resource_id, name, description, stewardship_type,"
            + " resource_type, cloning_instructions, attributes,"
            + " access_scope, managed_by, associated_app, assigned_user)"
            + " VALUES (:workspace_id, :cloud_platform, :resource_id, :name, :description, :stewardship_type,"
            + " :resource_type, :cloning_instructions, cast(:attributes AS jsonb),"
            + " :access_scope, :managed_by, :associated_app, :assigned_user)";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", resource.getWorkspaceId().toString())
            .addValue("cloud_platform", resource.getResourceType().getCloudPlatform().toString())
            .addValue("resource_id", resource.getResourceId().toString())
            .addValue("name", resource.getName())
            .addValue("description", resource.getDescription())
            .addValue("stewardship_type", resource.getStewardshipType().toSql())
            .addValue("resource_type", resource.getResourceType().toSql())
            .addValue("cloning_instructions", resource.getCloningInstructions().toSql())
            .addValue("attributes", resource.attributesToJson());
    if (resource.getStewardshipType().equals(CONTROLLED)) {
      ControlledResource controlledResource = resource.castToControlledResource();
      //noinspection deprecation
      params
          .addValue("access_scope", controlledResource.getAccessScope().toSql())
          .addValue("managed_by", controlledResource.getManagedBy().toSql())
          // TODO: add associatedApp to ControlledResource
          .addValue("associated_app", null)
          .addValue("assigned_user", controlledResource.getAssignedUser().orElse(null));
    } else {
      params
          .addValue("access_scope", null)
          .addValue("managed_by", null)
          .addValue("associated_app", null)
          .addValue("assigned_user", null);
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
    } catch (Exception e) {
      // TODO: This logging is intended to help diagnose Postgres crashes occurring on resource
      // creation.
      //  Once this issue is resolved, this should be removed.
      logger.info(
          "Failed to create resource workspace_id: {}, cloud_platform: {}, resource_id: {}, name: {}, description: {}, stewardship_type: {}, resource_type: {}, cloning_instructions: {}, attributes: {}",
          resource.getWorkspaceId().toString(),
          resource.getResourceType().getCloudPlatform().toString(),
          resource.getResourceId().toString(),
          resource.getName(),
          resource.getDescription(),
          resource.getStewardshipType().toSql(),
          resource.getResourceType().toSql(),
          resource.getCloningInstructions().toSql(),
          resource.attributesToJson());
      if (resource.getStewardshipType().equals(CONTROLLED)) {
        ControlledResource controlledResource = resource.castToControlledResource();
        logger.info(
            "Resource also has controlled-resource specific parameters access_scope: {}, managed_by: {}, associated_app: {}, assigned_user: {}",
            controlledResource.getAccessScope().toSql(),
            controlledResource.getManagedBy().toSql(),
            null,
            controlledResource.getAssignedUser().orElse(null));
      }
      // After logging, rethrow the exception
      logger.info("Resource creation failure is:", e);
      throw e;
    }
  }

  /**
   * Dispatch by stewardship and resource type to call the correct constructor for the WsmResource
   *
   * @param dbResource Resource data from the database
   * @return WsmResource
   */
  private WsmResource constructResource(DbResource dbResource) {
    switch (dbResource.getStewardshipType()) {
      case REFERENCED:
        switch (dbResource.getResourceType()) {
          case GCS_BUCKET:
            return new ReferencedGcsBucketResource(dbResource);

          case BIG_QUERY_DATASET:
            return new ReferencedBigQueryDatasetResource(dbResource);

          case DATA_REPO_SNAPSHOT:
            return new ReferencedDataRepoSnapshotResource(dbResource);

          default:
            throw new InvalidMetadataException(
                "Invalid reference resource type" + dbResource.getResourceType().toString());
        }

      case CONTROLLED:
        switch (dbResource.getResourceType()) {
          case GCS_BUCKET:
            return new ControlledGcsBucketResource(dbResource);
          case AI_NOTEBOOK_INSTANCE:
            return new ControlledAiNotebookInstanceResource(dbResource);
          case BIG_QUERY_DATASET:
            return new ControlledBigQueryDatasetResource(dbResource);
          case AZURE_IP:
            return new ControlledAzureIpResource(dbResource);
          case AZURE_DISK:
            return new ControlledAzureDiskResource(dbResource);
          default:
            throw new InvalidMetadataException(
                "Invalid controlled resource type" + dbResource.getResourceType().toString());
        }

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
}
