package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.db.exception.UnknownFlightOperationTypeException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceActivityLogDao {

  private static final String WORKSPACE_CREATED_DATE_SQL = "SELECT MIN(change_date) FROM workspace_activity_log"
      + " WHERE workspace_id = :workspace_id";
  private static final String WORKSPACE_LAST_UPDATED_SQL =
      "SELECT MAX(change_date) FROM workspace_activity_log"
          + " WHERE workspace_id = :workspace_id"
          + " AND change_type NOT IN (:change_type)";
  private final NamedParameterJdbcTemplate jdbcTemplate;

  // These fields don't update workspace "Last updated" time in UI. For example,
  // if a workspace reader is added, UI workspace "Last updated" time doesn't change.
  private static final Set<String> NON_UPDATE_TYPE_OPERATION =
      ImmutableSet.of(
          OperationType.GRANT_WORKSPACE_ROLE.name(),
          OperationType.REMOVE_WORKSPACE_ROLE.name(),
          OperationType.SYSTEM_CLEANUP.name());

  @Autowired
  public WorkspaceActivityLogDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
  public void writeActivity(UUID workspaceId, DbWorkspaceActivityLog dbWorkspaceActivityLog) {
    if (dbWorkspaceActivityLog.getOperationType() == OperationType.UNKNOWN) {
      throw new UnknownFlightOperationTypeException(
          String.format("Flight operation type is unknown in workspace %s", workspaceId));
    }
    final String sql =
        "INSERT INTO workspace_activity_log (workspace_id, change_date, change_type, change_agent_email)"
            + " VALUES (:workspace_id, :change_date, :change_type, :change_agent_email)";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_date", Instant.now().atOffset(ZoneOffset.UTC))
            .addValue("change_type", dbWorkspaceActivityLog.getOperationType().name())
            .addValue("change_agent_email", dbWorkspaceActivityLog.getUserEmail());
    jdbcTemplate.update(sql, params);
  }

  /**
   * Get the creation time of the given workspace.
   *
   * <p>In cases where workspace is created before activity logging is introduced, this method may
   * return empty or the first change activity logged since {@code #writeActivity} is implemented.
   */
  @ReadTransaction
  public Optional<OffsetDateTime> getCreatedDate(UUID workspaceId) {
    final var params = new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());
    return Optional.ofNullable(jdbcTemplate.queryForObject(WORKSPACE_CREATED_DATE_SQL, params, OffsetDateTime.class));
  }

  @ReadTransaction
  public Optional<String> getCreatedBy(UUID workspaceId) {
    final String sql =
        "SELECT change_agent_email FROM workspace_activity_log WHERE change_date = (" + WORKSPACE_CREATED_DATE_SQL + ")"
            + " AND workspace_id = :workspace_id";
    final var params = new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());
    return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, String.class));
  }

  @ReadTransaction
  public Optional<OffsetDateTime> getLastUpdatedDate(UUID workspaceId) {
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_type", NON_UPDATE_TYPE_OPERATION);
    return Optional.ofNullable(jdbcTemplate.queryForObject(WORKSPACE_LAST_UPDATED_SQL, params, OffsetDateTime.class));
  }

  public Optional<String> getLastUpdatedBy(UUID workspaceId) {
    final String sql =
        "SELECT change_agent_email FROM workspace_activity_log WHERE change_date = (" + WORKSPACE_LAST_UPDATED_SQL + ")"
            + " AND workspace_id = :workspace_id";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_type", NON_UPDATE_TYPE_OPERATION);
    return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, String.class));
  }


}
