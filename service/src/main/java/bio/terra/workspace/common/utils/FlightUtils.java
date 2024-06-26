package bio.terra.workspace.common.utils;

import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Stairway;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Common methods for building flights */
public final class FlightUtils {
  private static final Logger logger = LoggerFactory.getLogger(FlightUtils.class);

  // Parameters for waiting for subflight completion
  private static final Duration SUBFLIGHT_TOTAL_DURATION = Duration.ofHours(1);
  private static final Duration SUBFLIGHT_INITIAL_SLEEP = Duration.ofSeconds(10);
  private static final double SUBFLIGHT_FACTOR_INCREASE = 0.7;
  private static final Duration SUBFLIGHT_MAX_SLEEP = Duration.ofMinutes(2);

  public static final Duration CLONE_SUBFLIGHT_TOTAL_DURATION = Duration.ofMinutes(30);
  public static final Duration CLONE_SUBFLIGHT_INITIAL_SLEEP = Duration.ofSeconds(1);
  public static final double CLONE_SUBFLIGHT_FACTOR_INCREASE = 0.0; // fixed wait time
  public static final Duration CLONE_SUBFLIGHT_MAX_SLEEP = Duration.ofSeconds(1);

  // Parameters for waiting for JobService flight completion
  private static final Duration JOB_TOTAL_DURATION = Duration.ofHours(1);
  private static final Duration JOB_INITIAL_SLEEP = Duration.ofSeconds(1);
  private static final double JOB_FACTOR_INCREASE = 1;
  private static final Duration JOB_MAX_SLEEP = Duration.ofMinutes(2);

  private FlightUtils() {}

  /**
   * Build an error model and set it as the response
   *
   * @param context flight context
   * @param message error message
   * @param responseStatus status
   */
  public static void setErrorResponse(
      FlightContext context, String message, HttpStatus responseStatus) {
    ApiErrorReport errorModel = new ApiErrorReport().message(message);
    setResponse(context, errorModel, responseStatus);
  }

  /**
   * Set the response and status code in the result map.
   *
   * @param context flight context
   * @param responseObject response object to set
   * @param responseStatus status code to set
   */
  public static void setResponse(
      FlightContext context, Object responseObject, HttpStatus responseStatus) {
    FlightMap workingMap = context.getWorkingMap();
    workingMap.put(JobMapKeys.RESPONSE.getKeyName(), responseObject);
    workingMap.put(JobMapKeys.STATUS_CODE.getKeyName(), responseStatus);
  }

  /**
   * Get a supplied input value from input parameters, or, if that's missing, a default (previous)
   * value from the working map, or null.
   *
   * @param flightContext - context object for the flight, used to get the input & working maps
   * @param inputKey - key in input parameters for the supplied (override) value
   * @param workingKey - key in the working map for the previous value
   * @param klass - class of the value, e.g. String.class
   * @param <T> - type parameter corresponding to the klass
   * @return - a value from one of the two sources, or null
   */
  public static <T> T getInputParameterOrWorkingValue(
      FlightContext flightContext, String inputKey, String workingKey, Class<T> klass) {
    return Optional.ofNullable(flightContext.getInputParameters().get(inputKey, klass))
        .orElse(flightContext.getWorkingMap().get(workingKey, klass));
  }

  /**
   * Validation function, intended to be called from the top and bottom of a doStep() method in a
   * Step. Checks a list of input (or output) string keys to ensure they have a non-null value in
   * the map. For checking the input parameters map, the call can be made from a flight constructor.
   *
   * @param flightMap - either an input parameters or working map
   * @param keys - vararg of string keys to be checked
   */
  public static void validateRequiredEntries(FlightMap flightMap, String... keys) {
    for (String key : keys) {
      if (null == flightMap.getRaw(key)) {
        throw new MissingRequiredFieldsException(
            String.format("Required entry with key %s missing from flight map.", key));
      }
    }
  }

  public static FlightMap getResultMapRequired(FlightState flightState) {
    return flightState
        .getResultMap()
        .orElseThrow(
            () ->
                new MissingRequiredFieldsException(
                    String.format(
                        "ResultMap is missing for flight %s", flightState.getFlightId())));
  }

  /**
   * Get the error message from a FlightState's exception object if it exists. If there is no
   * message, return a message based off the exception's class name.
   *
   * @param subflightState - state of subflight
   * @return - error message or null for none
   */
  @Nullable
  public static String getFlightErrorMessage(FlightState subflightState) {
    String errorMessage = subflightState.getException().map(Throwable::getMessage).orElse(null);
    if (null == errorMessage && subflightState.getException().isPresent()) {
      // If the exception doesn't provide a message, we can scrape the class name at least.
      errorMessage =
          subflightState
              .getException()
              .map(Exception::getClass)
              .map(Class::getName)
              .map(s -> "Exception: " + s)
              .orElse(null);
    }
    return errorMessage;
  }

  /**
   * Get a value from one of the flight maps and check that it is not null. If it is null, throw.
   *
   * @param flightMap input or working map
   * @param key string key to lookup in the map
   * @param tClass class to return
   * @param <T> generic
   * @return T
   */
  public static <T> T getRequired(FlightMap flightMap, String key, Class<T> tClass) {
    var value = flightMap.get(key, tClass);
    if (value == null) {
      throw new MissingRequiredFieldsException("Missing required flight map key: " + key);
    }
    return value;
  }

  /**
   * Get a value from one of the flight maps and check that it is not null. If it is null, throw.
   *
   * @param flightMap input or working map
   * @param key string key to lookup in the map
   * @param typeReference Jackson type reference
   * @param <T> generic
   * @return T
   */
  public static <T> T getRequired(FlightMap flightMap, String key, TypeReference<T> typeReference) {
    var value = flightMap.get(key, typeReference);
    if (value == null) {
      throw new MissingRequiredFieldsException("Missing required flight map key: " + key);
    }
    return value;
  }

  /**
   * Wait with an exponential backoff. Useful for referenced resource creation/clone polling, as
   * that happens too quickly for even a 1-second fixed poll interval.
   *
   * @param stairway Stairway instance to probe
   * @param initialInterval initial interval to wait; doubled each wait
   * @param maxInterval maximum interval to wait
   * @param maxWait maximum time to wait
   */
  public static FlightState waitForFlightExponential(
      Stairway stairway,
      String flightId,
      Duration initialInterval,
      Duration maxInterval,
      Duration maxWait)
      throws Exception {
    return waitForFlightCompletion(
        stairway, flightId, maxWait, initialInterval, /* factorIncrease= */ 1.0, maxInterval);
  }

  /**
   * Utility method to wait for a job to complete. It is intended to be used by job service to wait
   * for flight completion.
   *
   * @param stairway stairway instance
   * @param flightId flight id to wait for
   * @return StepResult
   */
  public static FlightState waitForJobFlightCompletion(Stairway stairway, String flightId)
      throws Exception {
    return waitForFlightCompletion(
        stairway,
        flightId,
        JOB_TOTAL_DURATION,
        JOB_INITIAL_SLEEP,
        JOB_FACTOR_INCREASE,
        JOB_MAX_SLEEP);
  }

  /**
   * Utility method to wait for a subflight to complete. It is intended to be used in steps that
   * launch and then wait for flights. The `SubflightResult` return object reflects the success or
   * failure of the subflight.
   *
   * @param stairway stairway instance
   * @param flightId flight id to wait for
   * @return SubflightResult
   */
  public static SubflightResult waitForSubflightCompletion(Stairway stairway, String flightId)
      throws InterruptedException {
    return waitForSubflightCompletion(
        stairway,
        flightId,
        SUBFLIGHT_TOTAL_DURATION,
        SUBFLIGHT_INITIAL_SLEEP,
        SUBFLIGHT_FACTOR_INCREASE,
        SUBFLIGHT_MAX_SLEEP);
  }

  public static SubflightResult waitForSubflightCompletion(
      Stairway stairway,
      String flightId,
      Duration totalDuration,
      Duration initialSleep,
      double factorIncrease,
      Duration maxSleep)
      throws InterruptedException {
    try {
      FlightState flightState =
          waitForFlightCompletion(
              stairway, flightId, totalDuration, initialSleep, factorIncrease, maxSleep);
      return new SubflightResult(flightState);
    } catch (InterruptedException ie) {
      // Propagate the interrupt
      Thread.currentThread().interrupt();
      throw ie;
    } catch (Exception e) {
      return new SubflightResult(e);
    }
  }

  /**
   * Utility method to wait for a flight to complete. It is intended to be used in steps that launch
   * and then wait for flights. The StepReturn reflects the success or failure of the subflight.
   *
   * @param stairway stairway instance
   * @param flightId flight id to wait for
   * @return FlightState of completed flight
   */
  public static FlightState waitForFlightCompletion(
      Stairway stairway,
      String flightId,
      Duration totalDuration,
      Duration initialSleep,
      double factorIncrease,
      Duration maxSleep)
      throws Exception {
    return RetryUtils.getWithRetry(
        FlightUtils::flightComplete,
        () -> stairway.getFlightState(flightId),
        totalDuration,
        initialSleep,
        factorIncrease,
        maxSleep);
  }

  public static boolean flightComplete(FlightState flightState) {
    logger.info(
        "Testing flight {} completion; state is {}",
        flightState.getFlightId(),
        flightState.getFlightStatus());
    return !flightState.isActive();
  }

  public static SamUser getRequiredSamUser(FlightMap inputParameters, SamService samService) {
    AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    return samService.getSamUser(userRequest);
  }

  public static String getRequiredUserEmail(FlightMap inputParameters, SamService samService) {
    return getRequiredSamUser(inputParameters, samService).getEmail();
  }
}
