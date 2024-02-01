package bio.terra.workspace.common.utils;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import bio.terra.workspace.common.exception.MDCHandlingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A {@link StairwayHook} which does the following:
 *
 * <li>Propagates flight-specific mapped diagnostic context (MDC) across a Stairway flight's thread(s) for the duration
 *    of the flight.  This includes context provided by the caller in the input {@link FlightMap}, intended to store the
 *    MDC from the calling thread to preserve context across the duration of a request.
 * <li>Propagates step-specific MDC across a Stairway flight's thread(s) for the duration of each step.
 * <li>Supplements logging at notable flight state transitions.
 *
 * <p><b>Note for developers:</b> Any modifications to the MDC directly within flight or step code may not have their
 * intended effect and are not recommended (e.g. a flight may restart on a different thread due to failover-recovery).
 */
@Component
public class MdcHook implements StairwayHook {
  private static final Logger logger = LoggerFactory.getLogger(MdcHook.class);
  private static final String FLIGHT_LOG_FORMAT = "Operation: {}, flightClass: {}, flightId: {}";
  private static final String STEP_LOG_FORMAT =
      "Operation: {}, flightClass: {}, flightId: {}, stepClass: {}, stepIndex: {}, direction: {}";

  /** The key to use in the input {@link FlightMap} for storing the MDC context. */
  public static final String MDC_FLIGHT_MAP_KEY = "mdcKey";
  /** ID of the flight */
  public static final String FLIGHT_ID_KEY = "flightId";
  /** Class of the flight */
  public static final String FLIGHT_CLASS_KEY = "flightClass";
  /** Class of the flight step */
  public static final String FLIGHT_STEP_CLASS_KEY = "flightStepClass";
  /** Direction of the step (START, DO, SWITCH, or UNDO) */
  public static final String FLIGHT_STEP_DIRECTION_KEY = "flightStepDirection";
  /** The step's execution order */
  public static final String FLIGHT_STEP_NUMBER_KEY = "flightStepNumber";

  private static final TypeReference<Map<String, String>> mapType = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  @Autowired
  public MdcHook(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Returns a serialized version of the current MDC context.
   *
   * <p>This is meant to be used to set up the initial {@link FlightMap} when a Flight is being
   * created to propagate the context of the flight creation to the steps.
   */
  public Object getSerializedCurrentContext() {
    return serializeMdc(MDC.getCopyOfContextMap());
  }

  @Override
  public HookAction startStep(FlightContext flightContext) {
    addStepContextToMdc(flightContext);
    logger.info(
        STEP_LOG_FORMAT,
        "startStep",
        flightContext.getFlightClassName(),
        flightContext.getFlightId(),
        flightContext.getStepClassName(),
        flightContext.getStepIndex(),
        flightContext.getDirection().name());
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction endStep(FlightContext flightContext) {
    logger.info(
        STEP_LOG_FORMAT,
        "endStep",
        flightContext.getFlightClassName(),
        flightContext.getFlightId(),
        flightContext.getStepClassName(),
        flightContext.getStepIndex(),
        flightContext.getDirection().name());
    removeStepContextFromMdc();
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction startFlight(FlightContext flightContext) {
    String serializedMdc = flightContext.getInputParameters().get(MDC_FLIGHT_MAP_KEY, String.class);
    // Note that this destroys any previous context on this thread.
    MDC.setContextMap(deserializeMdc(serializedMdc));
    addFlightContextToMdc(flightContext);
    logger.info(
        FLIGHT_LOG_FORMAT,
        "startFlight",
        flightContext.getFlightClassName(),
        flightContext.getFlightId());
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction endFlight(FlightContext flightContext) {
    logger.info(
        FLIGHT_LOG_FORMAT,
        "endFlight",
        flightContext.getFlightClassName(),
        flightContext.getFlightId());
    MDC.clear();
    return HookAction.CONTINUE;
  }

  private String serializeMdc(@Nullable Map<String, String> mdcMap) {
    try {
      return objectMapper.writeValueAsString(mdcMap);
    } catch (JsonProcessingException e) {
      throw new MDCHandlingException(
          "Error serializing MDC map from string: "
              + (mdcMap == null ? "null" : mdcMap.toString()));
    }
  }

  private Map<String, String> deserializeMdc(@Nullable String serializedMdc) {
    if (serializedMdc == null) {
      return ImmutableMap.of();
    }
    Map<String, String> mdcContext;
    try {
      mdcContext = objectMapper.readValue(serializedMdc, mapType);
    } catch (JsonProcessingException e) {
      throw new MDCHandlingException("Error deserializing MDC map from string: " + serializedMdc);
    }
    if (mdcContext == null) {
      return ImmutableMap.of();
    }
    return mdcContext;
  }

  @Override
  public HookAction stateTransition(FlightContext context) {
    logger.info(
        "Flight ID {} changed status to {}.", context.getFlightId(), context.getFlightStatus());
    return HookAction.CONTINUE;
  }

  private void addFlightContextToMdc(FlightContext context) {
    MDC.put(FLIGHT_ID_KEY, context.getFlightId());
    MDC.put(FLIGHT_CLASS_KEY, context.getFlightClassName());
  }

  private void addStepContextToMdc(FlightContext context) {
    MDC.put(FLIGHT_STEP_CLASS_KEY, context.getStepClassName());
    MDC.put(FLIGHT_STEP_DIRECTION_KEY, context.getDirection().toString());
    MDC.put(FLIGHT_STEP_NUMBER_KEY, Integer.toString(context.getStepIndex()));
  }

  private void removeStepContextFromMdc() {
    MDC.remove(FLIGHT_STEP_CLASS_KEY);
    MDC.remove(FLIGHT_STEP_DIRECTION_KEY);
    MDC.remove(FLIGHT_STEP_NUMBER_KEY);
  }
}
