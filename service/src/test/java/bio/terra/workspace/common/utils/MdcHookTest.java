package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.stairway.*;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.job.JobService;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

class MdcHookTest extends BaseUnitTest {
  @Autowired private MdcHook mdcHook;
  @Autowired private JobService jobService;

  @Test
  void initialContextPropagated_Do() throws Exception {
    Stairway stairway = jobService.getStairway();

    MDC.clear();
    MDC.put("foo", "bar");
    FlightMap flightMap = new FlightMap();
    flightMap.put(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());

    String flightId = stairway.createFlightId();
    stairway.submit(flightId, MdcFooBarTestFlight.class, flightMap);
    Thread.sleep(3000);

    assertEquals(FlightStatus.SUCCESS, stairway.getFlightState(flightId).getFlightStatus());
  }

  @Test
  void initialContextPropagated_Undo() throws Exception {
    Stairway stairway = jobService.getStairway();

    MDC.clear();
    MDC.put("foo", "bar");
    FlightMap flightMap = new FlightMap();
    flightMap.put(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());

    String flightId = stairway.createFlightId();
    // Use a flight that always errors to check the MDC context on undo.
    stairway.submit(flightId, MdcFooBarUndoTestFlight.class, flightMap);
    Thread.sleep(3000);

    // CheckMDC passes on do & undo, otherwise the flight would end with FlightStatus.FATAL. The
    // ErrorStep causes the undo to happen and the flight to end as ERROR.
    assertEquals(FlightStatus.ERROR, stairway.getFlightState(flightId).getFlightStatus());
  }

  @Test
  void noInitialContextOk() throws Exception {
    Stairway stairway = jobService.getStairway();

    MDC.clear();
    FlightMap flightMap = new FlightMap();
    flightMap.put(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext());

    String flightId = stairway.createFlightId();
    stairway.submit(flightId, MdcNoContextTestFlight.class, flightMap);
    Thread.sleep(3000);

    assertEquals(FlightStatus.SUCCESS, stairway.getFlightState(flightId).getFlightStatus());
  }

  @Test
  void inputParametersNotSetOk() throws Exception {
    Stairway stairway = jobService.getStairway();

    String flightId = stairway.createFlightId();
    // Don't set the MDC_FLIGHT_MAP_KEY on the input FlightMap.
    stairway.submit(flightId, MdcNoContextTestFlight.class, new FlightMap());
    Thread.sleep(3000);

    assertEquals(FlightStatus.SUCCESS, stairway.getFlightState(flightId).getFlightStatus());
  }

  /** Test that the CheckMdc Step will fail the flight as expected by the rest of the tests. */
  @Test
  void testCheckMdc() throws Exception {
    Stairway stairway = jobService.getStairway();

    String flightId = stairway.createFlightId();
    // Don't set the MDC_FLIGHT_MAP_KEY on the input FlightMap, but expect "foo": "bar"
    stairway.submit(flightId, MdcFooBarTestFlight.class, new FlightMap());
    Thread.sleep(3000);

    // The CheckMdc step will fail during do & undo, causing a FATAL flight failure.
    assertEquals(FlightStatus.FATAL, stairway.getFlightState(flightId).getFlightStatus());
  }

  /** Flight to check that "foo": "bar" is in the MDC context. */
  public static class MdcFooBarTestFlight extends Flight {
    public MdcFooBarTestFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new CheckMdc(ImmutableMap.of("foo", "bar")));
    }
  }

  /** Flight to check that "foo": "bar" is in the MDC context with an error step. */
  public static class MdcFooBarUndoTestFlight extends Flight {
    public MdcFooBarUndoTestFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new CheckMdc(ImmutableMap.of("foo", "bar")));
      addStep(new ErrorStep());
    }
  }

  /** Flight to check that the MDC context has no values. */
  public static class MdcNoContextTestFlight extends Flight {
    public MdcNoContextTestFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new CheckMdc(ImmutableMap.of()));
    }
  }

  /** A step that asserts the MDC context is what's expected on do and undo. */
  public static class CheckMdc implements Step {
    private final ImmutableMap<String, String> expectedContext;

    public CheckMdc(ImmutableMap<String, String> expectedContext) {
      this.expectedContext = expectedContext;
    }

    @Override
    public StepResult doStep(FlightContext flightContext) {
      assertMatchesExpectedContext();
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      assertMatchesExpectedContext();
      return StepResult.getStepResultSuccess();
    }

    private void assertMatchesExpectedContext() {
      try {
        assertEquals(expectedContext, MDC.getCopyOfContextMap());
      } catch (AssertionError error) {
        // Rethrow an AssertionError as an Exception so that Stairway can handle it
        throw new RuntimeException(error);
      }
    }
  }

  /** A {@link Step} that always fails in a non-retryable way. */
  public static class ErrorStep implements Step {
    @Override
    public StepResult doStep(FlightContext flightContext) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      return StepResult.getStepResultSuccess();
    }
  }
}
