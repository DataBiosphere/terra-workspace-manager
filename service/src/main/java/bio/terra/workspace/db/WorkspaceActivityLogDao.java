package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.db.exception.UnknownFlightOperationTypeException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceActivityLogDao {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceActivityLogDao.class);
  private static final RowMapper<ActivityLogChangeDetails> ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER =
      (rs, rowNum) ->
          new ActivityLogChangeDetails()
              .dateTime(
                  OffsetDateTime.ofInstant(
                      rs.getTimestamp("change_date").toInstant(), ZoneId.of("UTC")))
              .userEmail(rs.getString("change_agent_email"))
              .userSubjectId(rs.getString("change_agent_subject_id"));
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
    logger.info(
        String.format(
            "#writeActivity: workspaceId=%s, operationType=%s, changeAgentEmail=%s, changeAgentSubjectId=%s",
            workspaceId,
            dbWorkspaceActivityLog.getOperationType(),
            dbWorkspaceActivityLog.getChangeAgentEmail(),
            dbWorkspaceActivityLog.getChangeAgentSubjectId()));
    if (dbWorkspaceActivityLog.getOperationType() == OperationType.UNKNOWN) {
      throw new UnknownFlightOperationTypeException(
          String.format("Flight operation type is unknown in workspace %s", workspaceId));
    }
    final String sql =
        "INSERT INTO workspace_activity_log (workspace_id, change_date, change_type, change_agent_email, change_agent_subject_id)"
            + " VALUES (:workspace_id, :change_date, :change_type, :change_agent_email, :change_agent_subject_id)";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_date", Instant.now().atOffset(ZoneOffset.UTC))
            .addValue("change_type", dbWorkspaceActivityLog.getOperationType().name())
            .addValue("change_agent_email", dbWorkspaceActivityLog.getChangeAgentEmail())
            .addValue("change_agent_subject_id", dbWorkspaceActivityLog.getChangeAgentSubjectId());
    jdbcTemplate.update(sql, params);
  }

  /**
   * Get the creation time of the given workspace.
   *
   * <p>In cases where workspace is created before activity logging is introduced, this method may
   * return empty or the first change activity logged since {@code #writeActivity} is implemented.
   */
  @ReadTransaction
  public Optional<ActivityLogChangeDetails> getCreateDetails(UUID workspaceId) {
    final String sql =
        "SELECT w.change_date, w.change_agent_email, w.change_agent_subject_id FROM workspace_activity_log w"
            + " JOIN (SELECT MIN(change_date) AS min_date FROM workspace_activity_log"
            + " WHERE workspace_id = :workspace_id) m"
            + " ON w.change_date = m.min_date";

    final var params = new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER)));
  }

  @ReadTransaction
  public Optional<ActivityLogChangeDetails> getLastUpdateDetails(UUID workspaceId) {
    final String sql =
        "SELECT w.change_agent_email, w.change_agent_subject_id, w.change_date FROM workspace_activity_log w"
            + " JOIN (SELECT MAX(change_date) AS max_date FROM workspace_activity_log"
            + " WHERE workspace_id = :workspace_id AND change_type NOT IN (:change_type)) m"
            + " ON w.change_date = m.max_date";

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_type", NON_UPDATE_TYPE_OPERATION);
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER)));
  }
}
