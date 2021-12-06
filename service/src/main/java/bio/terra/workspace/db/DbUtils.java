package bio.terra.workspace.db;

import bio.terra.common.exception.MissingRequiredFieldException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Utility functions for interacting with WSM's database */
public class DbUtils {

  /**
   * This method builds an SQL clause string for setting fields specified in the given parameters.
   * The method generates the column_name = :column_name list. It is an error if the params map is
   * empty.
   */
  public static String setColumnsClause(MapSqlParameterSource columnParams) {
    StringBuilder sb = new StringBuilder();
    String[] parameterNames = columnParams.getParameterNames();
    if (parameterNames.length == 0) {
      throw new MissingRequiredFieldException("Must specify some data to be updated.");
    }
    for (int i = 0; i < parameterNames.length; i++) {
      String columnName = parameterNames[i];
      if (i > 0) {
        sb.append(", ");
      }
      if (!columnName.equals("attributes")) {
        sb.append(columnName).append(" = :").append(columnName);
      } else {
        sb.append("attributes = cast(:attributes AS jsonb)");
      }
    }
    return sb.toString();
  }
}
