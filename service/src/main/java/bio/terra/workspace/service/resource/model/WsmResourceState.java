package bio.terra.workspace.service.resource.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiState;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.SerializationException;

/**
 * See design document <a
 * href="https://docs.google.com/document/d/1ply9gRK-X8luKOrCYbZAA90QGqoZwjyukCsrA5PbIyQ/edit#heading=h.ygdsvwmy6va0">here</a>
 */
public enum WsmResourceState {
  BROKEN("BROKEN", ApiState.BROKEN),
  CREATING("CREATING", ApiState.CREATING),
  DELETING("DELETING", ApiState.DELETING),
  READY("READY", ApiState.READY),
  UPDATING("UPDATING", ApiState.UPDATING),
  NOT_EXISTS("invalid not exists", null);

  // -- Valid state transitions --
  private static final List<WsmResourceState> BROKEN_TRANSITIONS = List.of(DELETING);
  // We allow the NOT_EXISTS transition when the state rule is DELETE_ON_FAILURE, where
  // we move failed resources from creating to non-existent.
  private static final List<WsmResourceState> CREATING_TRANSITIONS =
      List.of(BROKEN, READY, NOT_EXISTS);
  // We allow DELETING to READY to support the case of a successful undo of a delete
  // flight.
  private static final List<WsmResourceState> DELETING_TRANSITIONS = List.of(READY, NOT_EXISTS);
  private static final List<WsmResourceState> READY_TRANSITIONS = List.of(UPDATING, DELETING);
  private static final List<WsmResourceState> UPDATING_TRANSITIONS = List.of(BROKEN, READY);
  private static final List<WsmResourceState> NOT_EXISTS_TRANSITIONS = List.of(CREATING);

  private static final Map<WsmResourceState, List<WsmResourceState>> VALID_TRANSITIONS =
      Map.of(
          BROKEN, BROKEN_TRANSITIONS,
          CREATING, CREATING_TRANSITIONS,
          DELETING, DELETING_TRANSITIONS,
          READY, READY_TRANSITIONS,
          UPDATING, UPDATING_TRANSITIONS,
          NOT_EXISTS, NOT_EXISTS_TRANSITIONS);

  private final String dbString;
  private final ApiState apiState;

  WsmResourceState(String dbString, ApiState apiState) {
    this.dbString = dbString;
    this.apiState = apiState;
  }

  public static WsmResourceState fromDb(String dbString) {
    for (var state : WsmResourceState.values()) {
      if (state.dbString.equals(dbString)) {
        if (state == NOT_EXISTS) {
          throw new InternalLogicException("Found NOT_EXISTS state in the database");
        }
        return state;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching state type for " + dbString);
  }

  public String toDb() {
    return dbString;
  }

  public static WsmResourceState fromApi(ApiState apiState) {
    for (var state : WsmResourceState.values()) {
      if (state.apiState.equals(apiState)) {
        return state;
      }
    }
    throw new ValidationException("Invalid resource state: " + apiState);
  }

  public ApiState toApi() {
    return apiState;
  }

  public static boolean isValidTransition(WsmResourceState startState, WsmResourceState endState) {
    List<WsmResourceState> validTransitionList = VALID_TRANSITIONS.get(startState);
    if (validTransitionList == null) {
      throw new InternalLogicException("Invalid state");
    }
    // Allow self-transition for all states. This seems to be a common case for retries. We put this
    // in the lists if we decide it shouldn't hold for all states.
    if (startState == endState) {
      return true;
    }
    return validTransitionList.contains(endState);
  }
}
