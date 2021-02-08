package bio.terra.workspace.db;

import bio.terra.workspace.service.controlledresource.exception.DuplicateControlledResourceException;
import bio.terra.workspace.service.controlledresource.model.ControlledResourceMetadata;
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

  public UUID createControlledResource(ControlledResourceMetadata controlledResource) {
    final String sql =
        "INSERT INTO workspaceResource (workspaceId, resourceId, associatedApp, visible, owner, attributes) values "
            + "(:workspaceId, :resourceId, :associatedApp, :visible, :owner, :attributes)";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspaceId", controlledResource.workspaceId())
            .addValue("resourceId", controlledResource.resourceId())
            .addValue("visible", controlledResource.visible());
    controlledResource.associatedApp().ifPresent(a -> params.addValue("associatedApp", a));
    controlledResource.owner().ifPresent(o -> params.addValue("owner", o));
    controlledResource.attributes().ifPresent(a -> params.addValue("attributes", a));

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
    return controlledResource.resourceId();
  }
}
