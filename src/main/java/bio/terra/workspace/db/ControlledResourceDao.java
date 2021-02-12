package bio.terra.workspace.db;

import bio.terra.workspace.service.controlledresource.exception.DuplicateControlledResourceException;
import bio.terra.workspace.service.controlledresource.model.ControlledResourceMetadata;
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

@Component
public class ControlledResourceDao {
  private final Logger logger = LoggerFactory.getLogger(ControlledResourceDao.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public ControlledResourceDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void createControlledResource(ControlledResourceMetadata controlledResource) {
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

  public Optional<ControlledResourceMetadata> getControlledResource(UUID resourceId) {
    final String sql =
        "SELECT workspace_id, resource_id, associated_app, is_visible, owner, attributes "
            + "FROM workspace_resource "
            + "WHERE resource_id = :resourceId";
    final MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("resourceId", resourceId.toString());
    final Map<String, Object> columnToValue;
    try {
      columnToValue = jdbcTemplate.queryForMap(sql, params);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
    final ControlledResourceMetadata.Builder resultBuilder =
        ControlledResourceMetadata.builder()
            .setWorkspaceId(UUID.fromString((String) columnToValue.get("workspace_id")))
            .setResourceId(UUID.fromString((String) columnToValue.get("resource_id")))
            .setIsVisible((boolean) columnToValue.get("is_visible"))
            .setOwner((String) columnToValue.get("owner"));
    Optional.ofNullable((String) columnToValue.get("associated_app"))
        .ifPresent(resultBuilder::setAssociatedApp);
    // TODO: attributes support
    return Optional.of(resultBuilder.build());
  }
}
