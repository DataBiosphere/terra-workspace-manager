package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.stairway.*;
import bio.terra.workspace.common.BaseUnitTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

class MdcHookTest extends BaseUnitTest {
  @Autowired private MdcHook mdcHook;
  @Autowired private ObjectMapper objectMapper;

  private static final Map<String, String> FOO_BAR = Map.of("foo", "bar");

  private static final String FLIGHT_ID = TestUtils.appendRandomNumber("flightId");
  private static final String FLIGHT_CLASS = TestUtils.appendRandomNumber("flightClass");
  private static final Map<String, String> FLIGHT_MDC =
      Map.of(MdcHook.FLIGHT_ID_KEY, FLIGHT_ID, MdcHook.FLIGHT_CLASS_KEY, FLIGHT_CLASS);
  private static final int STEP_INDEX = 2;
  private static final Direction STEP_DIRECTION = Direction.DO;
  private static final String STEP_CLASS = TestUtils.appendRandomNumber("stepClass");
  private static final Map<String, String> STEP_MDC =
      Map.of(
          MdcHook.FLIGHT_STEP_NUMBER_KEY,
          Integer.toString(STEP_INDEX),
          MdcHook.FLIGHT_STEP_DIRECTION_KEY,
          STEP_DIRECTION.toString(),
          MdcHook.FLIGHT_STEP_CLASS_KEY,
          STEP_CLASS);

  private FlightMap inputParameters;
  private TestFlightContext flightContext;

  @BeforeEach
  void beforeEach() {
    MDC.clear();
    inputParameters = new FlightMap();
    flightContext =
        new TestFlightContext()
            .flightId(FLIGHT_ID)
            .flightClassName(FLIGHT_CLASS)
            .inputParameters(inputParameters)
            .stepIndex(STEP_INDEX)
            .direction(STEP_DIRECTION)
            .stepClassName(STEP_CLASS);
  }

  static Stream<Arguments> startFlight() {
    var parentFlight = new TestFlightContext();
    return Stream.of(
        // No request context
        Arguments.of(Map.of(), null, false),
        // Request context
        Arguments.of(FOO_BAR, null, false),
        // Request context and flight is a subflight
        Arguments.of(FOO_BAR, parentFlight, false),
        // Request context, flight is a subflight, and thread has existing context
        Arguments.of(FOO_BAR, parentFlight, true));
  }

  @ParameterizedTest
  @MethodSource
  void startFlight(
      @NotNull Map<String, String> requestContext,
      TestFlightContext parentFlight,
      boolean leftoverMdc)
      throws JsonProcessingException {
    var inputMapContext = new HashMap<>(requestContext);
    if (parentFlight != null) {
      inputMapContext.putAll(mdcHook.flightContextForMdc(parentFlight));
      inputMapContext.putAll(mdcHook.stepContextForMdc(parentFlight));
    }
    if (leftoverMdc) {
      MDC.put("leftover", "thread-context");
    }

    inputParameters.put(
        MdcHook.MDC_FLIGHT_MAP_KEY, objectMapper.writeValueAsString(inputMapContext));

    mdcHook.startFlight(flightContext);

    var expectedMdc = new HashMap<>(requestContext);
    expectedMdc.putAll(FLIGHT_MDC);
    assertEquals(
        expectedMdc, MDC.getCopyOfContextMap(), "Input map's context with new flight context");
  }

  static Stream<Map<String, String>> requestContext() {
    return Stream.of(Map.of(), FOO_BAR);
  }

  @ParameterizedTest
  @MethodSource("requestContext")
  void startStep(@NotNull Map<String, String> requestContext) {
    var initialMdc = new HashMap<>(requestContext);
    initialMdc.putAll(FLIGHT_MDC);
    MDC.setContextMap(initialMdc);

    mdcHook.startStep(flightContext);

    var expectedMdc = new HashMap<>(initialMdc);
    expectedMdc.putAll(STEP_MDC);
    assertEquals(expectedMdc, MDC.getCopyOfContextMap(), "Initial context with new step context");
  }

  @ParameterizedTest
  @MethodSource("requestContext")
  void endStep(@NotNull Map<String, String> requestContext) {
    var initialMdc = new HashMap<>(requestContext);
    initialMdc.putAll(FLIGHT_MDC);
    initialMdc.putAll(STEP_MDC);
    MDC.setContextMap(initialMdc);

    mdcHook.endStep(flightContext);

    var expectedMdc = new HashMap<>(requestContext);
    expectedMdc.putAll(FLIGHT_MDC);
    assertEquals(expectedMdc, MDC.getCopyOfContextMap(), "Initial context without step context");
  }

  @ParameterizedTest
  @MethodSource("requestContext")
  void endFlight(@NotNull Map<String, String> requestContext) {
    var initialMdc = new HashMap<>(requestContext);
    initialMdc.putAll(FLIGHT_MDC);
    MDC.setContextMap(initialMdc);

    mdcHook.endFlight(flightContext);
    assertNull(MDC.getCopyOfContextMap(), "Context cleared when flight ends");
  }
}
