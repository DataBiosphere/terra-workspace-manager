package bio.terra.workspace.common.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.stairway.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
public class FlightUtilsTest extends BaseMockitoStrictStubbingTest {

  @Mock private Stairway mockStairway;
  private FlightMap flightMap;
  private final UUID subFlightId = UUID.randomUUID();

  private FlightState createTestFlightState(FlightStatus status) {
    flightMap = new FlightMap();
    flightMap.put("testKey", "testValue");

    var flightState = new FlightState();
    flightState.setFlightId(subFlightId.toString());
    flightState.setFlightStatus(status);
    flightState.setResultMap(flightMap);
    return flightState;
  }

  @Test
  public void testWaitForSubflightCompletionSuccess() throws InterruptedException {
    var flightState = createTestFlightState(FlightStatus.SUCCESS);
    when(mockStairway.getFlightState(eq(subFlightId.toString()))).thenReturn(flightState);

    SubflightResult result =
        FlightUtils.waitForSubflightCompletion(mockStairway, subFlightId.toString());

    assertThat(result.isSuccess(), equalTo(true));
    assertThat(result.getFlightStatus(), equalTo(FlightStatus.SUCCESS));
    assertThat(result.getFlightErrorMessage(), equalTo(null));
    assertThat(result.convertToStepResult(), equalTo(StepResult.getStepResultSuccess()));
    assertThat(result.getFlightMap(), equalTo(flightMap));
  }

  @Test
  public void testWaitForSubflightCompletionIntermediaryStates() throws InterruptedException {
    var queuedState = createTestFlightState(FlightStatus.QUEUED);
    var readyToRunState = createTestFlightState(FlightStatus.READY);
    var runningState = createTestFlightState(FlightStatus.RUNNING);
    var successState = createTestFlightState(FlightStatus.SUCCESS);
    when(mockStairway.getFlightState(eq(subFlightId.toString())))
        .thenReturn(queuedState)
        .thenReturn(readyToRunState)
        .thenReturn(runningState)
        .thenReturn(successState);

    SubflightResult result =
        FlightUtils.waitForSubflightCompletion(
            mockStairway, subFlightId.toString(), Duration.ofMillis(200), Duration.ofSeconds(5));

    assertThat(result.isSuccess(), equalTo(true));
    assertThat(result.getFlightStatus(), equalTo(FlightStatus.SUCCESS));
    assertThat(result.getFlightErrorMessage(), equalTo(null));
    assertThat(result.convertToStepResult(), equalTo(StepResult.getStepResultSuccess()));
    assertThat(result.getFlightMap(), equalTo(flightMap));
  }

  @Test
  public void testWaitForSubflightCompletionError() throws InterruptedException {
    var errorState = createTestFlightState(FlightStatus.ERROR);
    var exception = new RuntimeException("Test error message");
    errorState.setException(exception);

    when(mockStairway.getFlightState(eq(subFlightId.toString()))).thenReturn(errorState);

    SubflightResult result =
        FlightUtils.waitForSubflightCompletion(mockStairway, subFlightId.toString());

    assertThat(result.isSuccess(), equalTo(false));
    assertThat(result.getFlightStatus(), equalTo(FlightStatus.ERROR));
    assertThat(result.getFlightErrorMessage(), equalTo("Test error message"));
    assertThat(result.getFlightMap(), equalTo(flightMap));

    StepResult stepResult = result.convertToStepResult();
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException(), equalTo(Optional.of(exception)));
  }

  @Test
  public void testWaitForSubflightCompletionInterruptedException() throws InterruptedException {

    when(mockStairway.getFlightState(eq(subFlightId.toString())))
        .thenThrow(new InterruptedException());

    assertThrows(
        InterruptedException.class,
        () -> FlightUtils.waitForSubflightCompletion(mockStairway, subFlightId.toString()));
  }

  @Test
  public void testWaitForSubflightCompletionUnhandledExceptionWithoutMessage()
      throws InterruptedException {

    Exception runtimeException = new RuntimeException();
    when(mockStairway.getFlightState(eq(subFlightId.toString()))).thenThrow(runtimeException);

    SubflightResult result =
        FlightUtils.waitForSubflightCompletion(mockStairway, subFlightId.toString());

    assertThat(result.isSuccess(), equalTo(false));
    assertThat(result.getFlightStatus(), equalTo(FlightStatus.FATAL));
    assertThat(result.getFlightErrorMessage(), equalTo("Flight failed with an empty exception"));
    assertThat(result.getFlightMap().isEmpty(), equalTo(true));

    StepResult stepResult = result.convertToStepResult();
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException(), equalTo(Optional.of(runtimeException)));
  }

  @Test
  public void testWaitForSubflightCompletionUnhandledExceptionWithMessage()
      throws InterruptedException {

    Exception runtimeException = new RuntimeException("test error message");
    when(mockStairway.getFlightState(eq(subFlightId.toString()))).thenThrow(runtimeException);

    SubflightResult result =
        FlightUtils.waitForSubflightCompletion(mockStairway, subFlightId.toString());

    assertThat(result.isSuccess(), equalTo(false));
    assertThat(result.getFlightStatus(), equalTo(FlightStatus.FATAL));
    assertThat(result.getFlightErrorMessage(), equalTo("test error message"));
    assertThat(result.getFlightMap().isEmpty(), equalTo(true));

    StepResult stepResult = result.convertToStepResult();
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException(), equalTo(Optional.of(runtimeException)));
  }
}
