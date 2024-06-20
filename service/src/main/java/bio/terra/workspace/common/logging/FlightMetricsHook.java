package bio.terra.workspace.common.logging;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Captures the duration and final status of stairway flights and steps for emission to a metrics
 * backend like prometheus
 */
@Component
public class FlightMetricsHook implements StairwayHook {
  private static final Logger logger = LoggerFactory.getLogger(FlightMetricsHook.class);
  private static final String FLIGHT_METRICS_KEY_PREFIX = "stairway.flight";
  private final MeterRegistry registry;

  public FlightMetricsHook(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public HookAction startStep(FlightContext context) {
    if (isContextInvalid(context)) {
      return HookAction.CONTINUE;
    }

    var stepMetrics = new TaskMetrics(OffsetDateTime.now());
    context.getWorkingMap().put(getStepMetricsKey(context), stepMetrics);

    return HookAction.CONTINUE;
  }

  @Override
  public HookAction endStep(FlightContext context) {
    if (isContextInvalid(context)) {
      return HookAction.CONTINUE;
    }

    var key = getStepMetricsKey(context);
    var stepMetrics = context.getWorkingMap().get(key, TaskMetrics.class);
    if (stepMetrics == null) {
      logger.warn(
          "No step metrics present for flightId {}, step {}",
          context.getFlightId(),
          context.getStepClassName());
      return HookAction.CONTINUE;
    }

    stepMetrics.setEndTime(OffsetDateTime.now());
    var stepStatus = context.getResult().getStepStatus();
    var tags =
        new ArrayList<>(
            List.of(
                Tag.of("status", stepStatus.toString()),
                Tag.of("flight", ClassUtils.getShortClassName(context.getFlightClassName()))));

    if (context
        .getInputParameters()
        .containsKey(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE)) {

      var resourceType =
          context
              .getInputParameters()
              .get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, String.class);
      tags.add(Tag.of("resourceType", resourceType));
    }
    emitTimer(stepMetrics, key, tags);

    return HookAction.CONTINUE;
  }

  @Override
  public HookAction startFlight(FlightContext context) {
    if (isContextInvalid(context)) {
      return HookAction.CONTINUE;
    }

    var flightMetrics = new TaskMetrics(OffsetDateTime.now());
    context.getWorkingMap().put(getFlightMetricsKey(context), flightMetrics);

    return HookAction.CONTINUE;
  }

  @Override
  public HookAction endFlight(FlightContext context) {
    if (isContextInvalid(context)) {
      return HookAction.CONTINUE;
    }

    var key = getFlightMetricsKey(context);
    var flightMetrics = context.getWorkingMap().get(key, TaskMetrics.class);
    if (flightMetrics == null) {
      logger.warn("No flight metrics present for flight {}", context.getFlightId());
      return HookAction.CONTINUE;
    }

    flightMetrics.setEndTime(OffsetDateTime.now());
    var tags = new ArrayList<>(List.of(Tag.of("status", context.getFlightStatus().toString())));
    if (context
        .getInputParameters()
        .containsKey(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE)) {

      var resourceType =
          context
              .getInputParameters()
              .get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, String.class);
      tags.add(Tag.of("resourceType", resourceType));
    }

    emitTimer(flightMetrics, key, tags);
    return HookAction.CONTINUE;
  }

  private void emitTimer(TaskMetrics flightMetrics, String key, List<Tag> tags) {
    var duration = flightMetrics.getDuration();
    if (duration == null) {
      return;
    }

    registry.timer(key + ".duration", tags).record(duration);
  }

  @VisibleForTesting
  String getFlightMetricsKey(FlightContext context) {
    return String.format(
        "%s.%s",
        FLIGHT_METRICS_KEY_PREFIX, ClassUtils.getShortClassName(context.getFlightClassName()));
  }

  @VisibleForTesting
  String getStepMetricsKey(FlightContext context) {
    var flightKeyPrefix = getFlightMetricsKey(context);
    return String.format(
        "%s.step.%s", flightKeyPrefix, ClassUtils.getShortClassName(context.getStepClassName()));
  }

  private boolean isContextInvalid(FlightContext context) {
    if (context == null || context.getWorkingMap() == null) {
      logger.warn("Flight context or working map null, skipping metrics hook");
      return true;
    }

    return false;
  }
}
