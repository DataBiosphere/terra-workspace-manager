package bio.terra.workspace.common.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RetryUtils provides static methods for waiting and retrying. */
public class RetryUtils {
  // Retry duration defaults - defaults are set for IAM propagation
  public static final Duration DEFAULT_RETRY_TOTAL_DURATION = Duration.ofMinutes(30);
  public static final Duration DEFAULT_RETRY_SLEEP_DURATION = Duration.ofSeconds(15);
  public static final double DEFAULT_RETRY_FACTOR_INCREASE = 0.0;
  public static final Duration DEFAULT_RETRY_SLEEP_DURATION_MAX = Duration.ofMinutes(3);
  private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

  /**
   * Get a result from a call that might throw an exception. If the supplier finishes, the result is
   * returned. If the supplier continues to throw, when totalDuration has elapsed, this method will
   * throw that exception.
   *
   * @param supplier - code returning the result or throwing an exception
   * @param totalDuration - total amount of time to retry
   * @param initialSleepDuration - initial amount of time to sleep between retries
   * @param factorIncrease - factor to increase the sleep time. The formula is: newSleepDuration =
   *     sleepDuration + (factorIncrease * sleepDuration) The default of 0.0 results in a fixed
   *     wait.
   * @param sleepDurationMax = the maximum duration to expand the sleep time.
   * @param retryExceptionList - nullable; a list of exception classes. If null, any exception is
   *     retried
   * @param <T> - type of result
   * @return - result from supplier, if no exception
   * @throws InterruptedException if the sleep is interrupted
   */
  public static <T> T getWithRetryOnException(
      SupplierWithException<T> supplier,
      Duration totalDuration,
      Duration initialSleepDuration,
      double factorIncrease,
      Duration sleepDurationMax,
      @Nullable List<Class<? extends Exception>> retryExceptionList)
      throws Exception {

    T result;
    Instant endTime = Instant.now().plus(totalDuration);
    Duration sleepDuration = initialSleepDuration;

    while (true) {
      try {
        result = supplier.get();
        break;
      } catch (Exception e) {
        // If we are out of time or the exception is not retryable
        if (Instant.now().isAfter(endTime) || !isRetryable(e, retryExceptionList)) {
          throw e;
        }
        logger.info(
            "Exception \"{}\". Waiting {} seconds. End time is {}",
            e.getMessage(),
            sleepDuration.toSeconds(),
            endTime);
        TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
        long increaseMillis = (long) (factorIncrease * sleepDuration.toMillis());
        sleepDuration = sleepDuration.plusMillis(increaseMillis);
        if (sleepDuration.compareTo(sleepDurationMax) > 0) {
          sleepDuration = sleepDurationMax;
        }
      }
    }
    return result;
  }

  /**
   * Default version of getWithRetryOnException. It retries all exceptions and uses the default
   * total duration and sleep duration.
   *
   * @param supplier - code returning the result or throwing an exception
   * @param <T> - type of result
   * @return - result from supplier
   * @throws InterruptedException if the sleep is interrupted
   */
  public static <T> T getWithRetryOnException(SupplierWithException<T> supplier) throws Exception {
    return getWithRetryOnException(
        supplier,
        DEFAULT_RETRY_TOTAL_DURATION,
        DEFAULT_RETRY_SLEEP_DURATION,
        DEFAULT_RETRY_FACTOR_INCREASE,
        DEFAULT_RETRY_SLEEP_DURATION_MAX,
        null);
  }

  /**
   * Get a result from a call that might throw an exception. If the supplier finishes, the result is
   * returned. If the supplier continues to throw, when totalDuration has elapsed, this method will
   * throw that exception.
   *
   * @param predicate - if evaluated true, then get the result; Otherwise, retry.
   * @param supplier - code returning the result or throwing an exception
   * @param totalDuration - total amount of time to retry
   * @param initialSleepDuration - initial amount of time to sleep between retries
   * @param factorIncrease - factor to increase the sleep time. The formula is: newSleepDuration =
   *     sleepDuration + (factorIncrease * sleepDuration) The default of 0.0 results in a fixed
   *     wait.
   * @param sleepDurationMax = the maximum duration to expand the sleep time.
   * @param <T> - type of result
   * @return - result from supplier, if no exception
   * @throws InterruptedException if the sleep is interrupted
   */
  public static <T> T getWithRetry(
      Predicate<T> predicate,
      SupplierWithException<T> supplier,
      Duration totalDuration,
      Duration initialSleepDuration,
      double factorIncrease,
      Duration sleepDurationMax)
      throws Exception {

    T result;
    Instant endTime = Instant.now().plus(totalDuration);
    Duration sleepDuration = initialSleepDuration;

    while (true) {
      result = supplier.get();
      if (predicate.test(result)) {
        break;
      } else {
        // If we are out of time
        if (Instant.now().isAfter(endTime)) {
          throw new Exception("retry timed out");
        }
        TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
        long increaseMillis = (long) (factorIncrease * sleepDuration.toMillis());
        sleepDuration = sleepDuration.plusMillis(increaseMillis);
        if (sleepDuration.compareTo(sleepDurationMax) > 0) {
          sleepDuration = sleepDurationMax;
        }
      }
    }
    return result;
  }

  /**
   * Default version of getWithRetry. It retries all evaluated predicate failures and uses the
   * default total duration and sleep duration.
   *
   * @param predicate - if evaluated true, then get the result; Otherwise, retry.
   * @param supplier - code returning the result or throwing an exception
   * @param <T> - type of result
   * @return - result from supplier
   * @throws InterruptedException if the sleep is interrupted
   */
  public static <T> T getWithRetry(Predicate<T> predicate, SupplierWithException<T> supplier)
      throws Exception {
    return getWithRetry(
        predicate,
        supplier,
        DEFAULT_RETRY_TOTAL_DURATION,
        DEFAULT_RETRY_SLEEP_DURATION,
        DEFAULT_RETRY_FACTOR_INCREASE,
        DEFAULT_RETRY_SLEEP_DURATION_MAX);
  }

  private static boolean isRetryable(
      Exception e, @Nullable List<Class<? extends Exception>> retryExceptionList) {
    // If we didn't get a list, then all exceptions are considered retryable
    if (retryExceptionList == null) {
      return true;
    }
    for (Class<? extends Exception> clazz : retryExceptionList) {
      if (clazz.isInstance(e)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Supplier that can throw
   *
   * @param <T> return type for the non-throw case
   */
  @FunctionalInterface
  public interface SupplierWithException<T> {
    T get() throws Exception;
  }
}
