package bio.terra.workspace.db;

import bio.terra.common.exception.MissingRequiredFieldException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Utility functions for interacting with WSM's database */
public class DbUtils {

  /**
   * This method builds an SQL clause string for setting fields specified in the given parameters.
   * The method generates the column_name = :column_name list. It is an error if the params map is
   * empty.
   *
   * @param columnParams map of sql parameters.
   * @param jsonColumns param columns that needs to be cast as jsonb.
   */
  public static String setColumnsClause(MapSqlParameterSource columnParams, String... jsonColumns) {
    StringBuilder sb = new StringBuilder();
    String[] parameterNames = columnParams.getParameterNames();
    if (parameterNames.length == 0) {
      throw new MissingRequiredFieldException("Must specify some data to be updated.");
    }
    Set<String> jsonColumnSet = new HashSet<>(Arrays.asList(jsonColumns));
    for (int i = 0; i < parameterNames.length; i++) {
      String columnName = parameterNames[i];
      if (i > 0) {
        sb.append(", ");
      }
      if (jsonColumnSet.contains(columnName)) {
        sb.append(columnName).append(" = cast(:").append(columnName).append(" AS jsonb)");
      } else {
        sb.append(columnName).append(" = :").append(columnName);
      }
    }

    return sb.toString();
  }
}
