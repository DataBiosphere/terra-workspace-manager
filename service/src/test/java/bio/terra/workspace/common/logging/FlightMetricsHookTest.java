package bio.terra.workspace.common.logging;

import static bio.terra.workspace.service.resource.model.WsmResourceType.CONTROLLED_AZURE_VM;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.annotations.BaseTest;
import bio.terra.workspace.common.utils.TestFlightContext;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@BaseTest
class FlightMetricsHookTest {

  FlightMetricsHook flightMetricsHook;
  MeterRegistry meterRegistry;

  @BeforeEach
  void setup() {
    meterRegistry = new SimpleMeterRegistry();
    flightMetricsHook = new FlightMetricsHook(meterRegistry);
  }

  @Test
  void startStep_success() {
    var context =
        new TestFlightContext()
            .flightClassName("bio.terra.testing.flight.TestFlight")
            .stepClassName("bio.terra.testing.StepClass");

    flightMetricsHook.startStep(context);
    var metrics =
        context
            .getWorkingMap()
            .get(flightMetricsHook.getStepMetricsKey(context), TaskMetrics.class);

    assertThat(metrics, notNullValue());
    assertThat(metrics.getDuration(), nullValue());
  }

  @Test
  void startFlight_success() {
    var context = new TestFlightContext().flightClassName("bio.terra.testing.flight.TestFlight");

    flightMetricsHook.startFlight(context);
    var metrics =
        context
            .getWorkingMap()
            .get(flightMetricsHook.getFlightMetricsKey(context), TaskMetrics.class);

    assertThat(metrics, notNullValue());
    assertThat(metrics.getDuration(), nullValue());
  }

  @Test
  void endStep_success() {
    var context =
        new TestFlightContext()
            .flightClassName("bio.terra.testing.flight.TestFlight")
            .stepClassName("bio.terra.testing.StepClass");

    flightMetricsHook.startStep(context);
    flightMetricsHook.endStep(context);
    var metrics =
        context
            .getWorkingMap()
            .get(flightMetricsHook.getStepMetricsKey(context), TaskMetrics.class);

    assertThat(metrics, notNullValue());
    var timer =
        meterRegistry.find(flightMetricsHook.getStepMetricsKey(context) + ".duration").timer();
    assertThat(timer, notNullValue());
    assertThat(
        timer.getId().getTag("status"), equalTo(context.getResult().getStepStatus().toString()));
  }

  @Test
  void endFlight_success() {
    var context =
        new TestFlightContext()
            .flightClassName("bio.terra.testing.flight.TestFlight")
            .stepClassName("bio.terra.testing.StepClass");

    flightMetricsHook.startFlight(context);
    flightMetricsHook.endFlight(context);
    var metrics =
        context
            .getWorkingMap()
            .get(flightMetricsHook.getFlightMetricsKey(context), TaskMetrics.class);

    assertThat(metrics, notNullValue());
    var timer =
        meterRegistry.find(flightMetricsHook.getFlightMetricsKey(context) + ".duration").timer();
    assertThat(timer, notNullValue());
    assertThat(timer.getId().getTag("status"), equalTo(context.getFlightStatus().toString()));
  }

  @Test
  void endFlight_capturesResourceTypeLabel() {
    var inputParams = new FlightMap();
    inputParams.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, CONTROLLED_AZURE_VM);
    var context =
        new TestFlightContext()
            .flightClassName("bio.terra.testing.flight.TestFlight")
            .stepClassName("bio.terra.testing.StepClass")
            .inputParameters(inputParams);

    flightMetricsHook.startFlight(context);
    flightMetricsHook.endFlight(context);

    var metrics =
        context
            .getWorkingMap()
            .get(flightMetricsHook.getFlightMetricsKey(context), TaskMetrics.class);

    assertThat(metrics, notNullValue());
    var timer =
        meterRegistry.find(flightMetricsHook.getFlightMetricsKey(context) + ".duration").timer();
    assertThat(timer, notNullValue());
    assertThat(timer.getId().getTag("status"), equalTo(context.getFlightStatus().toString()));
    assertThat(timer.getId().getTag("resourceType"), equalTo(CONTROLLED_AZURE_VM.toString()));
  }
}
