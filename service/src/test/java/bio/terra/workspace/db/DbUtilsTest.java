package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public class DbUtilsTest extends BaseUnitTest {

  @Test
  void setColumnsClause_withValidParams() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("key1", "value1");
    params.addValue("key2", "value2");

    assertEquals("key1 = :key1, key2 = :key2", DbUtils.setColumnsClause(params));
  }

  @Test
  void setColumnsClause_withJsonParams_jsonParamsIsInMapSqlParameterSource_castItToJson() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("key1", "value1");

    assertEquals("key1 = cast(:key1 AS jsonb)", DbUtils.setColumnsClause(params, "key1"));
  }

  @Test
  void
      setColumnsClause_withJsonParams_jsonParamsNotInMapSqlParameterSource_doesNotAddToColumnsClause() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("key1", "value1");
    params.addValue("key2", "value2");

    assertEquals("key1 = :key1, key2 = :key2", DbUtils.setColumnsClause(params, "key3"));
  }

  @Test
  void setColumnsClause_withMultipleJsonParams_succeeds() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("key1", "value1");
    params.addValue("key2", "value2");
    params.addValue("key3", "value3");

    assertEquals(
        "key1 = cast(:key1 AS jsonb), key2 = cast(:key2 AS jsonb), key3 = :key3",
        DbUtils.setColumnsClause(params, "key1", "key2"));
  }

  @Test
  void setColumnsClause_withEmptyParams_throwsException() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    assertThrows(MissingRequiredFieldException.class, () -> DbUtils.setColumnsClause(params));
  }
}
