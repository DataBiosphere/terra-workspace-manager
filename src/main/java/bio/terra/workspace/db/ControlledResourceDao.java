package bio.terra.workspace.db;

import bio.terra.workspace.service.resource.controlled.ControlledResourceDbModel;
import bio.terra.workspace.service.resource.controlled.exception.DuplicateControlledResourceException;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.Map;
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
 * DAO for the workspace_resource table TODO: migrate the table to include all fields necessary,
 * e.g. name and description.
 */
@Component
public class ControlledResourceDao {
  private final Logger logger = LoggerFactory.getLogger(ControlledResourceDao.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public ControlledResourceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void createControlledResource(ControlledResourceDbModel controlledResource) {
    final String sql =
        "INSERT INTO workspace_resource "
            + "(workspace_id, resource_id, associated_app, is_visible, owner, attributes) values "
            + "(:workspaceId, :resourceId, :associatedApp, :isVisible, :owner, :attributes)";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspaceId", controlledResource.getWorkspaceId())
            .addValue("resourceId", controlledResource.getResourceId())
            .addValue("isVisible", controlledResource.isVisible())
            .addValue("associatedApp", controlledResource.getAssociatedApp().orElse(null))
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

  public Optional<ControlledResourceDbModel> getControlledResource(UUID resourceId) {
    final String sql =
        "SELECT workspace_id, resource_id, associated_app, is_visible, owner, attributes "
            + "FROM workspace_resource "
            + "WHERE resource_id = :resourceId";
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resourceId", resourceId.toString());
    final Map<String, Object> columnToValue;
    try {
      // TODO: move away from queryForMap() to avoid catch and casting
      columnToValue = jdbcTemplate.queryForMap(sql, params);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
    final ControlledResourceDbModel.Builder resultBuilder =
        ControlledResourceDbModel.builder()
            .setWorkspaceId(UUID.fromString((String) columnToValue.get("workspace_id")))
            .setResourceId(UUID.fromString((String) columnToValue.get("resource_id")))
            .setIsVisible((boolean) columnToValue.get("is_visible"))
            .setOwner((String) columnToValue.get("owner"));
    Optional.ofNullable((String) columnToValue.get("associated_app"))
        .ifPresent(resultBuilder::setAssociatedApp);
    // TODO: attributes support
    return Optional.of(resultBuilder.build());
  }

  public boolean deleteControlledResource(UUID resourceId) {
    final String sql = "DELETE FROM workspace_resource WHERE resource_id = :resourceId";
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resourceId", resourceId.toString());
    return jdbcTemplate.update(sql, params) == 1;
  }

  /** Truncate the table. For testing only. */
  @VisibleForTesting
  public void truncate() {
    final String sql = "TRUNCATE TABLE workspace_resource";
    jdbcTemplate.update(sql, Collections.emptyMap());
  }
}
