package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.*;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.job.JobService;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

class MdcHookTest extends BaseUnitTest {
  @Autowired private MdcHook mdcHook;
  @Autowired private JobService jobService;

  private static final Map<String, String> FOO_BAR = Map.of("foo", "bar");
  private Stairway stairway;
  private String flightId;
  private FlightMap flightMap;

  @BeforeEach
  void beforeEach() {
    MDC.clear();
    stairway = jobService.getStairway();
    flightId = stairway.createFlightId();
    flightMap = new FlightMap();
    flightMap.put(MdcHook.FLIGHT_ID_KEY, flightId);
  }

  @Test
  void inputContextPropagated_Do() throws Exception {
    MDC.setContextMap(FOO_BAR);
    flightMap.put(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());

    assertEquals(
        FlightStatus.SUCCESS,
        submitAndWait(MdcFooBarTestFlight.class).getFlightStatus(),
        "Input context, flight context, and step context propagated");
    assertEquals(
        FOO_BAR, MDC.getCopyOfContextMap(), "Calling thread's context is preserved after flight");
  }

  @Test
  void inputContextPropagated_Undo() throws Exception {
    MDC.setContextMap(FOO_BAR);
    flightMap.put(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());

    // Use a flight that always errors to check the MDC context on undo.
    // CheckMDC passes on do & undo, otherwise the flight would end with FlightStatus.FATAL. The
    // ErrorStep causes the undo to happen and the flight to end as ERROR.
    var flightState = submitAndWait(MdcFooBarUndoTestFlight.class);
    assertEquals(
        FlightStatus.ERROR,
        flightState.getFlightStatus(),
        "Flight fails but all steps can be walked back");
    var maybeFlightException = flightState.getException();
    assertTrue(maybeFlightException.isPresent());
    assertEquals(
        ErrorStep.EXPECTED_EXCEPTION.getMessage(),
        maybeFlightException.get().getMessage(),
        "Flight fails due to ErrorStep");
    assertEquals(
        FOO_BAR, MDC.getCopyOfContextMap(), "Calling thread's context is preserved after flight");
  }

  @Test
  void emptyInputContextOk() throws Exception {
    flightMap.put(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());

    assertEquals(
        FlightStatus.SUCCESS,
        submitAndWait(MdcNoInputContextTestFlight.class).getFlightStatus(),
        "Flight succeeds when empty input context provided");
    assertNull(MDC.getCopyOfContextMap(), "Calling thread's context is preserved after flight");
  }

  @Test
  void inputParametersNotSetOk() throws Exception {
    assertEquals(
        FlightStatus.SUCCESS,
        submitAndWait(MdcNoInputContextTestFlight.class).getFlightStatus(),
        "Flight succeeds when no input context provided");
    assertNull(MDC.getCopyOfContextMap(), "Calling thread's context is preserved after flight");
  }

  /** Test that the CheckMdc Step will fail the flight as expected by the rest of the tests. */
  @Test
  void testCheckMdc() throws Exception {
    // Don't set the MDC_FLIGHT_MAP_KEY on the input FlightMap, but expect "foo": "bar"
    // The CheckMdc step will fail during do & undo, causing a FATAL flight failure.
    assertEquals(
        FlightStatus.FATAL,
        submitAndWait(MdcFooBarTestFlight.class).getFlightStatus(),
        "Flight fails fatally without expected input context");
    assertNull(MDC.getCopyOfContextMap(), "calling thread's context is preserved after flight");
  }

  /** Flight to check that "foo": "bar" is in the MDC context. */
  public static class MdcFooBarTestFlight extends Flight {
    public MdcFooBarTestFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      var expectedFlightContext =
          ImmutableMap.<String, String>builder()
              .putAll(FOO_BAR)
              .put(MdcHook.FLIGHT_ID_KEY, inputParameters.get(MdcHook.FLIGHT_ID_KEY, String.class))
              .put(MdcHook.FLIGHT_CLASS_KEY, getClass().getName())
              .build();
      addStep(new CheckMdc(expectedFlightContext, 0, Direction.SWITCH));
    }
  }

  /** Flight to check that "foo": "bar" is in the MDC context with an error step. */
  public static class MdcFooBarUndoTestFlight extends Flight {
    public MdcFooBarUndoTestFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      var expectedFlightContext =
          ImmutableMap.<String, String>builder()
              .putAll(FOO_BAR)
              .put(MdcHook.FLIGHT_ID_KEY, inputParameters.get(MdcHook.FLIGHT_ID_KEY, String.class))
              .put(MdcHook.FLIGHT_CLASS_KEY, getClass().getName())
              .build();
      addStep(new CheckMdc(expectedFlightContext, 0, Direction.UNDO));
      addStep(new ErrorStep(expectedFlightContext, 1, Direction.SWITCH));
    }
  }

  /**
   * Flight to check Stairway MDC context modifications when no initial context was provided as a
   * flight input.
   */
  public static class MdcNoInputContextTestFlight extends Flight {
    public MdcNoInputContextTestFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      var expectedFlightContext =
          ImmutableMap.<String, String>builder()
              .put(MdcHook.FLIGHT_ID_KEY, inputParameters.get(MdcHook.FLIGHT_ID_KEY, String.class))
              .put(MdcHook.FLIGHT_CLASS_KEY, getClass().getName())
              .build();
      addStep(new CheckMdc(expectedFlightContext, 0, Direction.SWITCH));
    }
  }

  /** A step that asserts the MDC context is what's expected on do and undo. */
  public static class CheckMdc extends MdcAssertionStep {

    public CheckMdc(
        Map<String, String> expectedFlightContext,
        Integer expectedStepNumber,
        Direction expectedUndoDirection) {
      super(expectedFlightContext, expectedStepNumber, expectedUndoDirection);
    }

    @Override
    public StepResult doStep(FlightContext flightContext) {
      assertMatchesExpectedContext(Direction.DO);
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      assertMatchesExpectedContext(expectedUndoDirection);
      return StepResult.getStepResultSuccess();
    }
  }

  /** A {@link Step} that always fails in a non-retryable way. */
  public static class ErrorStep extends MdcAssertionStep {

    public static final Exception EXPECTED_EXCEPTION =
        new RuntimeException("Expected exception on ErrorStep.doStep");

    public ErrorStep(
        Map<String, String> expectedFlightContext,
        Integer expectedStepNumber,
        Direction expectedUndoDirection) {
      super(expectedFlightContext, expectedStepNumber, expectedUndoDirection);
    }

    @Override
    public StepResult doStep(FlightContext flightContext) {
      assertMatchesExpectedContext(Direction.DO);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, EXPECTED_EXCEPTION);
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      assertMatchesExpectedContext(expectedUndoDirection);
      return StepResult.getStepResultSuccess();
    }
  }

  private abstract static class MdcAssertionStep implements Step {
    private final Map<String, String> expectedContextBase;
    final Direction expectedUndoDirection;

    MdcAssertionStep(
        Map<String, String> expectedFlightContext,
        Integer expectedStepNumber,
        Direction expectedUndoDirection) {
      this.expectedContextBase =
          ImmutableMap.<String, String>builder()
              .putAll(expectedFlightContext)
              .put(MdcHook.FLIGHT_STEP_CLASS_KEY, getClass().getName())
              .put(MdcHook.FLIGHT_STEP_NUMBER_KEY, expectedStepNumber.toString())
              .build();
      this.expectedUndoDirection = expectedUndoDirection;
    }

    void assertMatchesExpectedContext(Direction flightStepDirection) {
      try {
        var expectedContext =
            ImmutableMap.<String, String>builder()
                .putAll(expectedContextBase)
                .put(MdcHook.FLIGHT_STEP_DIRECTION_KEY, flightStepDirection.name())
                .build();
        assertEquals(expectedContext, MDC.getCopyOfContextMap());
      } catch (AssertionError error) {
        // Rethrow an AssertionError as an Exception so that Stairway can handle it
        throw new RuntimeException(error);
      }
    }
  }

  /**
   * Submits the flight and polls every 100 ms for its completion, up to 3 seconds.
   *
   * @param flightClass to submit with flightId and flightMap instance variables
   * @return FlightState of the completed flight
   */
  private FlightState submitAndWait(Class<? extends Flight> flightClass) throws Exception {
    stairway.submit(flightId, flightClass, flightMap);
    return FlightUtils.waitForFlightCompletion(
        stairway,
        flightId,
        Duration.ofSeconds(3),
        Duration.ofMillis(100),
        0,
        Duration.ofMillis(100));
  }
}
