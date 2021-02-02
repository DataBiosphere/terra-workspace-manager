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
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A {@link StairwayHook} for propagating MDC context across steps using the input {@link
 * FlightMap}.
 *
 * <p>This allows steps to have the same MDC context as when their flight was created. Note that any
 * modifications to the MDC context within a step are not propagated to other steps.
 */
@Component
public class MdcHook implements StairwayHook {
  /** The key to use in {@link FlightMap} for storing the MDC context. */
  public static final String MDC_FLIGHT_MAP_KEY = "mdcKey";

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
  public @NotNull HookAction startStep(@NotNull FlightContext flightContext) {
    String serializedMdc = flightContext.getInputParameters().get(MDC_FLIGHT_MAP_KEY, String.class);
    // Note that this destroys any previous context on this thread.
    MDC.setContextMap(deserializeMdc(serializedMdc));
    return HookAction.CONTINUE;
  }

  @Override
  public @NotNull HookAction endStep(FlightContext flightContext) {
    MDC.clear();
    return HookAction.CONTINUE;
  }

  @Override
  public @NotNull HookAction startFlight(FlightContext flightContext) {
    // Do nothing for flights.
    return HookAction.CONTINUE;
  }

  @Override
  public @NotNull HookAction endFlight(FlightContext flightContext) {
    // Do nothing for flights.
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
}
