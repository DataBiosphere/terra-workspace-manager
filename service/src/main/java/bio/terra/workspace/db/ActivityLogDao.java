package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
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
  private final RowMapper<Instant> UPDATE_DATE_ROW_MAPPER =
      (rs, rowNum) -> rs.getObject("update_date", OffsetDateTime.class).toInstant();

  @Autowired
  public ActivityLogDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WriteTransaction
  public void setUpdateDate(String workspaceId) {
    setUpdateDate(workspaceId, Instant.now());
  }

  @WriteTransaction
  public void setUpdateDate(String workspaceId, Instant instant) {
    final String sql =
        "INSERT INTO workspace_activity_log (workspace_id, update_date)"
            + " VALUES (:workspace_id, :update_date)";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId)
            .addValue("update_date", instant.atOffset(ZoneOffset.UTC));
    jdbcTemplate.update(sql, params);
  }

  @ReadTransaction
  public @Nullable Instant getLastUpdateDate(String workspaceId) {
    final String sql =
        "SELECT workspace_id, update_date"
            + " FROM workspace_activity_log WHERE workspace_id = :workspace_id "
            + " ORDER BY update_date DESC LIMIT 1";
    final var params = new MapSqlParameterSource().addValue("workspace_id", workspaceId);

    return DataAccessUtils.uniqueResult(jdbcTemplate.query(sql, params, UPDATE_DATE_ROW_MAPPER));
  }
}
