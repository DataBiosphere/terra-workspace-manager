package bio.terra.workspace.common.utils;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/** Common methods for building flights */
public final class FlightUtils {
  private FlightUtils() {}

  /**
   * Build an error model and set it as the response
   *
   * @param context
   * @param message
   * @param responseStatus
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
   * Step. Checks a list of input (or output) string keys to ensure they have a non-null value in the map.
   * For checking the input parameters map, the call can be made from a flight constructor.
   * @param flightMap - either an input parameters or working map
   * @param keys - vararg of string keys to be checked
   */
  public static void validateRequiredEntriesNonNull(FlightMap flightMap, String... keys) {
    for (String key : keys) {
      if (null == flightMap.getRaw(key)) {
        throw new MissingRequiredFieldsException("Required entry with key %s missing from flight map");
      }
    }
  }
}
