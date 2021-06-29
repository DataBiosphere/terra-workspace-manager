package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseUnitTest;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@SuppressWarnings(value = "unchecked")
public class DbRetryUtilsTest extends BaseUnitTest {

  static String FAKE_QUERY = "INSERT INTO (fake_row, other_fake_row) VALUES (:foo, :bar)";
  static MapSqlParameterSource FAKE_PARAMS =
      new MapSqlParameterSource().addValue("foo", "foo").addValue("bar", "bar");
  static RowMapper<Integer> FAKE_ROW_MAPPER = (rs, rowNum) -> 42;

  private static final CannotSerializeTransactionException RETRY_EXCEPTION =
      new CannotSerializeTransactionException("test");

  @Mock NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void updateSucceedsWithRetry() throws InterruptedException {
    when(jdbcTemplate.update(any(), (SqlParameterSource) any()))
        .thenThrow(RETRY_EXCEPTION)
        .thenReturn(42);
    assertEquals(42, DbRetryUtils.retry(() -> jdbcTemplate.update(FAKE_QUERY, FAKE_PARAMS)));
  }

  @Test
  void querySucceedsWithRetry() throws InterruptedException {
    when(jdbcTemplate.query(any(), (SqlParameterSource) any(), (RowMapper) any()))
        .thenThrow(RETRY_EXCEPTION)
        .thenReturn(Collections.singletonList(42));
    assertEquals(
        Collections.singletonList(42),
        DbRetryUtils.retry(() -> jdbcTemplate.query(FAKE_QUERY, FAKE_PARAMS, FAKE_ROW_MAPPER)));
  }

  @Test
  void queryForObjectByClassSucceedsWithRetry() throws InterruptedException {
    when(jdbcTemplate.queryForObject(any(), (SqlParameterSource) any(), (Class) any()))
        .thenThrow(RETRY_EXCEPTION)
        .thenReturn(42);
    assertEquals(
        42,
        DbRetryUtils.retry(
            () -> jdbcTemplate.queryForObject(FAKE_QUERY, FAKE_PARAMS, Integer.class)));
  }

  @Test
  void queryForObjectByRowMapperSucceedsWithRetry() throws InterruptedException {
    when(jdbcTemplate.queryForObject(any(), (SqlParameterSource) any(), (RowMapper) any()))
        .thenThrow(RETRY_EXCEPTION)
        .thenReturn(42);
    assertEquals(
        42,
        DbRetryUtils.retry(
            () -> jdbcTemplate.queryForObject(FAKE_QUERY, FAKE_PARAMS, FAKE_ROW_MAPPER)));
  }

  @Test
  void errorOnRetriesExceeded() {
    var exceptionArray = new CannotSerializeTransactionException[DbRetryUtils.MAX_ATTEMPTS + 1];
    Arrays.fill(exceptionArray, RETRY_EXCEPTION);
    when(jdbcTemplate.update(any(), (SqlParameterSource) any()))
        .thenThrow(exceptionArray)
        .thenReturn(42);
    DataAccessException ex =
        assertThrows(
            DataAccessException.class,
            () -> DbRetryUtils.retry(() -> jdbcTemplate.update(FAKE_QUERY, FAKE_PARAMS)));
    assertEquals(RETRY_EXCEPTION, ex);
  }
}
