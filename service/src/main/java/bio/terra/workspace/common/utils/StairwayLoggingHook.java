package bio.terra.workspace.common.utils;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** A {@link StairwayHook} which supplements logging at notable flight state transitions. */
@Component
public class StairwayLoggingHook implements StairwayHook {
  private static final Logger logger = LoggerFactory.getLogger(StairwayLoggingHook.class);
  private static final String FLIGHT_LOG_FORMAT = "Operation: {}, flightClass: {}, flightId: {}";
  private static final String STEP_LOG_FORMAT =
      "Operation: {}, flightClass: {}, flightId: {}, stepClass: {}, stepIndex: {}, direction: {}";

  @Override
  public HookAction startStep(FlightContext flightContext) {
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
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction startFlight(FlightContext flightContext) {
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
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction stateTransition(FlightContext context) {
    logger.info(
        "Flight ID {} changed status to {}.", context.getFlightId(), context.getFlightStatus());
    return HookAction.CONTINUE;
  }
}
