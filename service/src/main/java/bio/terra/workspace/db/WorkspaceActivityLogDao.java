package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.db.exception.UnknownFlightOperationTypeException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceActivityLogDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public WorkspaceActivityLogDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
  public void writeActivity(UUID workspaceId, DbWorkspaceActivityLog dbWorkspaceActivityLog) {
    if (dbWorkspaceActivityLog.getOperationType() == OperationType.UNKNOWN) {
      throw new UnknownFlightOperationTypeException("Flight operation type is unknown");
    }
    final String sql =
        "INSERT INTO workspace_activity_log (workspace_id, change_date, change_type)"
            + " VALUES (:workspace_id, :change_date, :change_type)";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_date", Instant.now().atOffset(ZoneOffset.UTC))
            .addValue("change_type", dbWorkspaceActivityLog.getOperationType().name());
    jdbcTemplate.update(sql, params);
  }

  @ReadTransaction
  public Optional<Instant> getLastUpdatedDate(UUID workspaceId) {
    final String sql =
        "SELECT MAX(change_date) FROM workspace_activity_log WHERE workspace_id = :workspace_id";
    final var params = new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());

    return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, OffsetDateTime.class))
        .map(OffsetDateTime::toInstant);
  }
}
