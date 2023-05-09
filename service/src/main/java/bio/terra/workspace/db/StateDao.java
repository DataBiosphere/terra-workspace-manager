package bio.terra.workspace.db;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.exception.ResourceStateConflictException;
import bio.terra.workspace.db.model.DbStateful;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** State DAO provides common methods for inspecting and updating state. */
@Component
public class StateDao {
  private static final Logger logger = LoggerFactory.getLogger(StateDao.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public StateDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Check if the resource is already in the target state with the matching flight id. There are two
   * cases. In the NOT_EXISTS case, we see if the resource is not there. In all other cases, we see
   * if the resource is there in the expected state.
   *
   * @param dbStateful data fetched from the object row or null
   * @param state expected state
   * @param flightId expected flightId
   * @return true if the resource is in the expected state; false otherwise
   */
  public boolean isResourceInState(
      @Nullable DbStateful dbStateful, WsmResourceState state, String flightId) {
    if (dbStateful == null) {
      return (state == WsmResourceState.NOT_EXISTS && flightId == null);
    }

    return (dbStateful.getState() == state
        && StringUtils.equals(dbStateful.getFlightId(), flightId));
  }

  public void updateState(
      @Nullable DbStateful dbStateful,
      @Nullable String expectedFlightId,
      @Nullable String targetFlightId,
      WsmResourceState targetState,
      @Nullable Exception exception) {
    // If we are already in the target state, assume this is a retry and go on our merry way
    if (isResourceInState(dbStateful, targetState, targetFlightId)) {
      return;
    }

    if (dbStateful == null) {
      throw new InternalLogicException("Unexpected database state - resource not found");
    }

    // Ensure valid state transition
    if (!isValidStateTransition(dbStateful, expectedFlightId, targetState)) {
      throw new ResourceStateConflictException(
          String.format(
              "%s (%s) is in state %s flightId %s; expected flightId %s; cannot transition to state %s flightId %s",
              dbStateful.getObjectTypeString(),
              dbStateful.getObjectId(),
              dbStateful.getState(),
              dbStateful.getFlightId(),
              expectedFlightId,
              targetState,
              targetFlightId));
    }

    // Normalize any exception into a serialized error report
    String errorReport;
    if (exception == null) {
      errorReport = null;
    } else if (exception instanceof ErrorReportException ex) {
      errorReport = DbSerDes.toJson(ex);
    } else {
      errorReport =
          DbSerDes.toJson(
              new InternalLogicException(
                  (exception.getMessage() == null ? "<no message>" : exception.getMessage())));
    }

    var params =
        new MapSqlParameterSource()
            .addValue("new_state", targetState.toDb())
            .addValue("new_flight_id", targetFlightId)
            .addValue("expected_flight_id", expectedFlightId)
            .addValue("error", DbSerDes.toJson(errorReport));

    String predicate = dbStateful.makeSqlRowPredicate(params);

    String sql =
        String.format(
            "UPDATE %s SET state = :new_state, flight_id = :new_flight_id, error = :error WHERE %s",
            dbStateful.getTableName(), predicate);

    if (expectedFlightId == null) {
      sql = sql + " AND flight_id IS NULL";
    } else {
      sql = sql + " AND flight_id = :expected_flight_id";
    }

    int rowsAffected = jdbcTemplate.update(sql, params);
    if (rowsAffected != 1) {
      throw new InternalLogicException("Unexpected database state - no row updated");
    }

    logger.info(
        "{} ({}) state change: New state {}; flightId {}; error {}",
        dbStateful.getObjectTypeString(),
        dbStateful.getObjectId(),
        targetState,
        targetFlightId,
        errorReport);
  }

  /**
   * Test that the dbResource and flight id are as expected, and that the transition to the target
   * state is valid
   *
   * @param dbStateful data fetched from the object row or null
   * @param expectedFlightId what we expect the flightId to be
   * @param targetState what we want to set the target state to be
   * @return true if the transition is valid
   */
  private boolean isValidStateTransition(
      @Nullable DbStateful dbStateful,
      @Nullable String expectedFlightId,
      WsmResourceState targetState) {
    // No transitions from a non-existant resource
    if (dbStateful == null) {
      logger.info("State conflict: object does not exist");
      return false;
    }

    // If the state transition is allowed and the flight matches, then we allow
    // the transition.
    if (WsmResourceState.isValidTransition(dbStateful.getState(), targetState)
        && StringUtils.equals(dbStateful.getFlightId(), expectedFlightId)) {
      return true;
    }

    logger.info(
        "{} state conflict. Id: {} CurrentState: {} TargetState: {}",
        dbStateful.getObjectTypeString(),
        dbStateful.getObjectId(),
        dbStateful.getState(),
        targetState);
    return false;
  }
}
