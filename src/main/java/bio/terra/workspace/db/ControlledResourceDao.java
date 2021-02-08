package bio.terra.workspace.db;

import bio.terra.workspace.service.controlledresource.model.ControlledResourceMetadata;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ControlledResourceDao {

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Autowired
  public ControlledResourceDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
    this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
  }

  public UUID createControlledResource(ControlledResourceMetadata controlledResourceMetadata) {
    final String sql =
        "INSERT INTO workspaceResource (workspaceId, resourceId, associatedApp, visible, owner, attributes) values "
            + "(:workspaceId, :resourceId, :associatedApp, :visible, :owner, :attributes)";
    //    final var params = new MapSqlParameterSource()
    //        .addValue("workspaceId", );
    return UUID.randomUUID(); // fixme
  }
}
