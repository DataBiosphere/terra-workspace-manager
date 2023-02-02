package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.db.exception.UnknownFlightOperationTypeException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.opencensus.contrib.spring.aop.Traced;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceActivityLogDao {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceActivityLogDao.class);
  // Only rows that existed before the column was added will have "unknown"
  private static final String DEFAULT_VALUE_UNKNOWN = "unknown";
  private static final String SELECT_SQL =
      """
      SELECT workspace_id, change_date, actor_email, actor_subject_id, change_subject_id, change_subject_type, change_type
            FROM workspace_activity_log
            """;

  @VisibleForTesting
  public static final RowMapper<ActivityLogChangeDetails> ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER =
      (rs, rowNum) -> {
        String changeSubjectTypeString = rs.getString("change_subject_type");
        var changeSubjectType =
            DEFAULT_VALUE_UNKNOWN.equals(changeSubjectTypeString)
                ? null
                : ActivityLogChangedTarget.valueOf(changeSubjectTypeString);
        String changeTypeString = rs.getString("change_type");
        OperationType changeType =
            DEFAULT_VALUE_UNKNOWN.equals(changeTypeString)
                ? OperationType.UNKNOWN
                : OperationType.valueOf(changeTypeString);
        return new ActivityLogChangeDetails(
            UUID.fromString(rs.getString("workspace_id")),
            OffsetDateTime.ofInstant(rs.getTimestamp("change_date").toInstant(), ZoneId.of("UTC")),
            rs.getString("actor_email"),
            rs.getString("actor_subject_id"),
            changeType,
            rs.getString("change_subject_id"),
            changeSubjectType);
      };

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

  @Traced
  @WriteTransaction
  public void writeActivity(UUID workspaceId, DbWorkspaceActivityLog dbWorkspaceActivityLog) {
    logger.info(
        String.format(
            "Writing activity log: workspaceId=%s, activityLog=%s",
            workspaceId, dbWorkspaceActivityLog.toString()));
    if (dbWorkspaceActivityLog.operationType() == OperationType.UNKNOWN) {
      throw new UnknownFlightOperationTypeException(
          String.format("Flight operation type is unknown in workspace %s", workspaceId));
    }
    final String sql =
        """
            INSERT INTO workspace_activity_log (
              workspace_id, change_date, change_type, actor_email, actor_subject_id,
              change_subject_id, change_subject_type)
            VALUES (:workspace_id, :change_date, :change_type, :actor_email, :actor_subject_id,
              :change_subject_id, :change_subject_type)
        """;
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_date", Instant.now().atOffset(ZoneOffset.UTC))
            .addValue("change_type", dbWorkspaceActivityLog.operationType().name())
            .addValue("actor_email", dbWorkspaceActivityLog.actorEmail())
            .addValue("actor_subject_id", dbWorkspaceActivityLog.actorSubjectId())
            .addValue("change_subject_id", dbWorkspaceActivityLog.changeSubjectId())
            .addValue(
                "change_subject_type",
                Optional.ofNullable(dbWorkspaceActivityLog.changeSubjectType())
                    .map(ActivityLogChangedTarget::name)
                    .orElse(null));
    try {
      jdbcTemplate.update(sql, params);
    } catch (DataIntegrityViolationException e) {
      throw new InternalServerErrorException(
          "Invalid input: failed insert new row to WorkspaceActivityLog table", e);
    }
  }

  @Traced
  @ReadTransaction
  public Optional<ActivityLogChangeDetails> getLastUpdatedDetails(UUID workspaceId) {
    final String sql =
        SELECT_SQL
            + """
            WHERE workspace_id = :workspace_id AND change_type NOT IN (:change_type)
            ORDER BY change_date DESC
            LIMIT 1
        """;

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_type", NON_UPDATE_TYPE_OPERATION);
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER)));
  }

  /** Get the last update details of a given change subject in a given workspace. */
  @Traced
  @ReadTransaction
  public Optional<ActivityLogChangeDetails> getLastUpdatedDetails(
      UUID workspaceId, String changeSubjectId) {
    final String sql =
        SELECT_SQL
            + """
            WHERE workspace_id = :workspace_id AND change_subject_id = :change_subject_id
            ORDER BY change_date DESC
            LIMIT 1
        """;

    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("change_subject_id", changeSubjectId);
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(sql, params, ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER)));
  }

  /**
   * List all the workspaces cloned from a given workspace. The list is retrieved recursively. If A
   * is cloned by B and B is cloned by C, both B and C are returned.
   *
   * @param sourceWorkspaceId the source workspace
   */
  public List<ActivityLogChangeDetails> listCloneLogFromSource(UUID sourceWorkspaceId) {
    String sql =
        String.format(
            """
         WITH RECURSIVE subWorkspaceActivityLogs AS (
                 %s
                 WHERE change_subject_id = :source_workspace_id
                 AND change_type = :change_type
                 UNION
                   SELECT e.workspace_id, e.change_date, e.actor_email, e.actor_subject_id, e.change_subject_id, e.change_subject_type, e.change_type
                   FROM workspace_activity_log e
                   INNER JOIN subWorkspaceActivityLogs s ON s.workspace_id = e.change_subject_id
         ) SELECT * FROM subWorkspaceActivityLogs
       """,
            SELECT_SQL);
    var params = new MapSqlParameterSource();
    params
        .addValue("source_workspace_id", sourceWorkspaceId.toString())
        .addValue("change_type", OperationType.CLONE.name());
    return ImmutableList.copyOf(
        jdbcTemplate.query(sql, params, ACTIVITY_LOG_CHANGE_DETAILS_ROW_MAPPER));
  }
}
