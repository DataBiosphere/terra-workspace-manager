package bio.terra.workspace.db.model;

import bio.terra.workspace.service.resource.model.WsmResourceState;
import javax.annotation.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Interface for DB objects that implement states */
public interface DbStateful {
  /**
   * Get the state of the DB object
   *
   * @return state
   */
  WsmResourceState getState();

  /**
   * Get the flightId of the DB object
   *
   * @return nullable flight id
   */
  @Nullable
  String getFlightId();

  /** Get the text string of the type of object for logging */
  String getObjectTypeString();

  /** Get a usefully unique name or id of the object, for logging and error messages */
  String getObjectId();

  /** Get the table name for state updates */
  String getTableName();

  /**
   * Construct a SQL predicate that identifies the row in the table The result would be something
   * like:
   *
   * <pre>
   *  workspace_id = :workspace_id AND resource_id = :resource_id
   * </pre>
   *
   * <p>
   *
   * @param params Parameter map to add where-clause parameters
   * @return SQL predicate
   */
  String makeSqlRowPredicate(MapSqlParameterSource params);
}
