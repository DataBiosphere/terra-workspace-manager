package bio.terra.workspace.db;

import bio.terra.common.db.DatabaseRetryUtils;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * This class contains wrappers around JDBC methods which run them with retries. Only DB exceptions
 * considered retryable ({@link RecoverableDataAccessException} and {@link
 * org.springframework.dao.TransientDataAccessException}) are retried. See Spring documentation of
 * these exceptions for more information:
 * https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/DataAccessException.html
 *
 * <p>Retries are with no delay, up to a certain maximum number of retries.
 */
public class DbRetryUtils {

  public static int MAX_RETRIES = 10;

  public static int updateWithRetries(
      NamedParameterJdbcTemplate jdbcTemplate, String sql, MapSqlParameterSource params) {
    try {
      return DatabaseRetryUtils.executeAndRetry(
          () -> jdbcTemplate.update(sql, params), Duration.ZERO, MAX_RETRIES);
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Got an InterruptedException without any threads sleeping in updateWithRetries. This should never happen.",
          e);
    }
  }

  public static <T> List<T> queryWithRetries(
      NamedParameterJdbcTemplate jdbcTemplate,
      String sql,
      MapSqlParameterSource params,
      RowMapper<T> rowMapper) {
    try {
      return DatabaseRetryUtils.executeAndRetry(
          () -> jdbcTemplate.query(sql, params, rowMapper), Duration.ZERO, MAX_RETRIES);
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Got an InterruptedException without any threads sleeping in queryWithRetries. This should never happen.",
          e);
    }
  }

  public static <T> T queryForObjectWithRetries(
      NamedParameterJdbcTemplate jdbcTemplate,
      String sql,
      MapSqlParameterSource params,
      Class<T> clazz) {
    try {
      return DatabaseRetryUtils.executeAndRetry(
          () -> jdbcTemplate.queryForObject(sql, params, clazz), Duration.ZERO, MAX_RETRIES);
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Got an InterruptedException without any threads sleeping in queryForObjectWithRetries. This should never happen.",
          e);
    }
  }

  public static <T> T queryForObjectWithRetries(
      NamedParameterJdbcTemplate jdbcTemplate,
      String sql,
      MapSqlParameterSource params,
      RowMapper<T> rowMapper) {
    try {
      return DatabaseRetryUtils.executeAndRetry(
          () -> jdbcTemplate.queryForObject(sql, params, rowMapper), Duration.ZERO, MAX_RETRIES);
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Got an InterruptedException without any threads sleeping in queryForObjectWithRetries. This should never happen.",
          e);
    }
  }
}
