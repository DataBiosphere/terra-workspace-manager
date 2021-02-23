package bio.terra.workspace.db;

import bio.terra.workspace.service.resource.controlled.ControlledResourceDbModel;
import bio.terra.workspace.service.resource.controlled.exception.DuplicateControlledResourceException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DAO for the workspace_resource table. It should be used in conjunction with the DataReferenceDao,
 * as that table is the primary entry point to workspace_resource.
 */
@Component
public class ControlledResourceDao {
  private final Logger logger = LoggerFactory.getLogger(ControlledResourceDao.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public ControlledResourceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Create a controlled resource. Calls to this method Should be followed by calls to
   * bio.terra.workspace.db.DataReferenceDao#createDataReference()
   *
   * @param controlledResource database model to be created.
   */
  public void createControlledResource(ControlledResourceDbModel controlledResource) {
    // the is_visible column is deprecated, but required
    final String sql =
        "INSERT INTO workspace_resource "
            + "(workspace_id, resource_id, owner, attributes, is_visible) values "
            + "(:workspaceId, :resourceId, :owner, :attributes, true)";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspaceId", controlledResource.getWorkspaceId())
            .addValue("resourceId", controlledResource.getResourceId())
            .addValue("owner", controlledResource.getOwner().orElse(null))
            .addValue("attributes", controlledResource.getAttributes());

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          String.format(
              "Inserted record for workspaceResource %s",
              controlledResource.getResourceId().toString()));
    } catch (DuplicateKeyException e) {
      throw new DuplicateControlledResourceException(
          String.format(
              "Resource %s already exists in workspace %s",
              controlledResource.getResourceId(), controlledResource.getWorkspaceId()),
          e);
    }
  }

  /**
   * Retrieve a controlled resource by its unique ID, if present.
   *
   * @param resourceId ID for this controlled resource
   * @return Optional - present if found
   */
  public Optional<ControlledResourceDbModel> getControlledResource(UUID resourceId) {
    final String sql =
        "SELECT workspace_id, resource_id, owner, attributes "
            + "FROM workspace_resource "
            + "WHERE resource_id = :resourceId";
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resourceId", resourceId.toString());
    final ControlledResourceDbModel controlledResourceDbModel;
    try {
      controlledResourceDbModel =
          jdbcTemplate.queryForObject(
              sql,
              params,
              (rs, rowNum) ->
                  ControlledResourceDbModel.builder()
                      .setWorkspaceId(UUID.fromString(rs.getString("workspace_id")))
                      .setResourceId(UUID.fromString(rs.getString("resource_id")))
                      .setOwner(rs.getString("owner"))
                      .setAttributes(rs.getString("attributes"))
                      .build());
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
    return Optional.ofNullable(controlledResourceDbModel);
  }

  /**
   * Delete a controlled resource
   *
   * @param resourceId - unidque ID of resource
   * @return true if delete occurred, false otherwise (for example if not found)
   */
  public boolean deleteControlledResource(UUID resourceId) {
    final String sql = "DELETE FROM workspace_resource WHERE resource_id = :resourceId";
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resourceId", resourceId.toString());
    return jdbcTemplate.update(sql, params) == 1;
  }
}
