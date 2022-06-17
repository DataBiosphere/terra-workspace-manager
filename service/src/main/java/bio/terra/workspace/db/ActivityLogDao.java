package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.db.model.ActivityLogChangedType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ActivityLogDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final RowMapper<Instant> CHANGE_DATE_ROW_MAPPER =
      (rs, rowNum) -> rs.getObject("change_date", OffsetDateTime.class).toInstant();

  @Autowired
  public ActivityLogDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
  public void setChangedDate(String workspaceId, @Nullable ActivityLogChangedType changeType) {
    if (changeType == null) {
      return;
    }
    final String sql =
        "INSERT INTO activity_log (workspace_id, change_date, change_type)"
            + " VALUES (:workspace_id, :change_date, :change_type)";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId)
            .addValue("change_date", Instant.now().atOffset(ZoneOffset.UTC))
            .addValue("change_type", changeType.name());
    jdbcTemplate.update(sql, params);
  }

  @ReadTransaction
  public @Nullable Instant getLastChangedDate(String workspaceId) {
    return getLastUpdatedInstanceByTable(workspaceId);
  }

  private Instant getLastUpdatedInstanceByTable(String workspaceId) {
    final String sql =
        "SELECT change_date FROM activity_log WHERE workspace_id = :workspace_id "
            + " ORDER BY change_date DESC LIMIT 1";
    final var params = new MapSqlParameterSource().addValue("workspace_id", workspaceId);

    return DataAccessUtils.uniqueResult(jdbcTemplate.query(sql, params, CHANGE_DATE_ROW_MAPPER));
  }
}
