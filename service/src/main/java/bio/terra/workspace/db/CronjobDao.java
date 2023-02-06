package bio.terra.workspace.db;

import bio.terra.common.db.WriteTransaction;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Data Access Object for interacting with the cronjob table of the database. */
@Component
public class CronjobDao {
  private static final Logger logger = LoggerFactory.getLogger(CronjobDao.class);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public CronjobDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Attempt to claim the latest run of a cron job in the database.
   *
   * <p>This method reads the timestamp of the most recent run of the specified job. If {@code
   * timeSinceLastRun} has elapsed since the run, this method will write the current time as the
   * latest timestamp in the table and return true. If less than {@timeSinceLastRun} has elapsed
   * this will return false without modifying the database.
   *
   * <p>Jobs which have never been run before are treated as if the last run was at Instant.EPOCH
   * (1970-01-01T00:00:00Z).
   *
   * <p>This method uses the {@WriteTransaction} annotation to ensure the read and write queries are
   * executed as a single transaction.
   *
   * @param jobName Name of the job being claimed
   * @param timeSinceLastRun If this much time has elapsed since the previous job execution, claim
   *     the latest job run. Otherwise, do nothing.
   * @return true if {@timeSinceLastRun} has elapsed since the job's last execution, false otherwise
   */
  @WriteTransaction
  public boolean claimJob(String jobName, Duration timeSinceLastRun) {
    String readQuery = "SELECT date_last_run FROM cronjob_state WHERE cronjob_name = :cronjob_name";
    MapSqlParameterSource readParams =
        new MapSqlParameterSource().addValue("cronjob_name", jobName);
    Timestamp lastJobRun;
    try {
      lastJobRun = jdbcTemplate.queryForObject(readQuery, readParams, Timestamp.class);
    } catch (EmptyResultDataAccessException e) {
      lastJobRun = Timestamp.from(Instant.EPOCH);
    }
    if (lastJobRun != null
        && lastJobRun.toInstant().plus(timeSinceLastRun).isAfter(Instant.now())) {
      // Job has been run more recently, do nothing.
      return false;
    }
    String upsertQuery =
        "INSERT INTO cronjob_state (cronjob_name, date_last_run) VALUES (:cronjob_name, :timestamp) "
            + "ON CONFLICT (cronjob_name) "
            + "DO UPDATE SET date_last_run = :timestamp";
    MapSqlParameterSource upsertParams =
        new MapSqlParameterSource()
            .addValue("cronjob_name", jobName)
            .addValue("timestamp", Timestamp.from(Instant.now()));
    jdbcTemplate.update(upsertQuery, upsertParams);
    return true;
  }
}
