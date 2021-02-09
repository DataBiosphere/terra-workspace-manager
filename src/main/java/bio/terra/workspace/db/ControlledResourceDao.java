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
            .addValue("workspaceId", controlledResource.workspaceId())
            .addValue("resourceId", controlledResource.resourceId())
            .addValue("isVisible", controlledResource.isVisible())
            .addValue("associatedApp", controlledResource.associatedApp().orElse(null))
            .addValue("owner", controlledResource.owner().orElse(null))
            .addValue("attributes", controlledResource.attributes().orElse(null));

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          String.format(
              "Inserted record for workspaceResource %s",
              controlledResource.resourceId().toString()));
    } catch (DuplicateKeyException e) {
      throw new DuplicateControlledResourceException(
          String.format(
              "Resource %s already exists in workspace %s",
              controlledResource.resourceId(), controlledResource.workspaceId()),
          e);
    }
  }

  public Optional<ControlledResourceMetadata> getControlledResource(
      UUID workspaceId, UUID resourceId) {
    final String sql =
        "SELECT workspace_id, resource_id, associated_app, is_visible, owner, attributes "
            + "FROM workspace_resource "
            + "WHERE workspace_id = :workspaceId AND resource_id = :resourceId";
    final MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspaceId", workspaceId.toString())
            .addValue("resourceId", resourceId.toString());
    final Map<String, Object> columnToValue = jdbcTemplate.queryForMap(sql, params);
    if (columnToValue.isEmpty()) {
      return Optional.empty();
    } else {
      final ControlledResourceMetadata.Builder resultBuilder =
          ControlledResourceMetadata.builder()
              .workspaceId(UUID.fromString((String) columnToValue.get("workspace_id")))
              .resourceId(UUID.fromString((String) columnToValue.get("resource_id")))
              .isVisible((boolean) columnToValue.get("is_visible"))
              .owner((String) columnToValue.get("owner"));
      Optional.ofNullable((String) columnToValue.get("associated_app"))
          .ifPresent(resultBuilder::associatedApp);
      // TODO: attributes support
      return Optional.of(resultBuilder.build());
    }
  }
}
