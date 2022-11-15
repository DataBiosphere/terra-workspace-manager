package bio.terra.workspace.common.utils;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import com.fasterxml.jackson.core.type.TypeReference;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

/**
 * This class wraps the Stairway flight class and provides additional features. Some of these should
 * probably move into Stairway, but for now...
 */
public class WsmFlight extends Flight {
  private final FlightBeanBag beanBag;

  public WsmFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    this.beanBag = FlightBeanBag.getFromObject(applicationContext);
  }

  /**
   * Getter for the properly typed FlightBeanBag
   *
   * @return flight bean bag
   */
  public FlightBeanBag beanBag() {
    return beanBag;
  }

  /**
   * Override addStep and make it public, so we can delegate step generation to subroutines instead
   * of having it all in the flight object. That allows more composable flights.
   *
   * @param step Step object
   * @param retryRule retry rule
   */
  @Override
  public void addStep(Step step, RetryRule retryRule) {
    super.addStep(step, retryRule);
  }

  @Override
  public void addStep(Step step) {
    super.addStep(step);
  }

  /**
   * Get a required input that cannot be null
   *
   * @param key string key to lookup
   * @param type class of the result
   * @return T result value from map
   */
  @NotNull
  public <T> T getInputRequired(String key, Class<T> type) {
    if (getInputParameters().containsKey(key)) {
      T result = getInputParameters().get(key, type);
      if (result != null) {
        return result;
      }
    }
    throw new MissingRequiredFieldsException(
        String.format("Required flight input parameter key %s is null or missing", key));
  }

  @NotNull
  public <T> T getInputRequired(String key, TypeReference<T> typeReference) {
    if (getInputParameters().containsKey(key)) {
      T result = getInputParameters().get(key, typeReference);
      if (result != null) {
        return result;
      }
    }
    throw new MissingRequiredFieldsException(
        String.format("Required flight input parameter key %s is null or missing", key));
  }

  /**
   * Get a required input that can be null
   *
   * @param key string key to lookup
   * @param type class of the result
   * @return T result value from map
   */
  @Nullable
  public <T> T getInputRequiredNullable(String key, Class<T> type) {
    if (getInputParameters().containsKey(key)) {
      return getInputParameters().get(key, type);
    }
    throw new MissingRequiredFieldsException(
        String.format("Required flight input parameter key %s is missing", key));
  }

  @NotNull
  public <T> T getInputRequiredNullable(String key, TypeReference<T> typeReference) {
    if (getInputParameters().containsKey(key)) {
      return getInputParameters().get(key, typeReference);
    }
    throw new MissingRequiredFieldsException(
        String.format("Required flight input parameter key %s is missing", key));
  }
}
