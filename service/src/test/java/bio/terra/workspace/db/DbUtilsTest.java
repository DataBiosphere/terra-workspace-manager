package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public class DbUtilsTest extends BaseUnitTest {

  @Test
  void setClauseWithValidParams() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("key1", "value1");
    params.addValue("key2", "value2");

    assertEquals("key1 = :key1, key2 = :key2", DbUtils.setColumnsClause(params));
  }

  @Test
  void setClauseWithEmptyParamsThrows() {
    MapSqlParameterSource params = new MapSqlParameterSource();
    assertThrows(MissingRequiredFieldException.class, () -> DbUtils.setColumnsClause(params));
  }
}
