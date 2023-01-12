package bio.terra.workspace.common.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RetryUtils provides static methods for waiting and retrying. */
public class RetryUtils {
  // Retry duration defaults - defaults are set for IAM propagation
  public static final Duration DEFAULT_RETRY_TOTAL_DURATION = Duration.ofMinutes(7);
  public static final Duration DEFAULT_SLEEP_DURATION = Duration.ofSeconds(15);

  private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

  /**
   * Supplier that can throw
   *
   * @param <T> return type for the non-throw case
   */
  @FunctionalInterface
  public interface SupplierWithException<T> {
    T get() throws Exception;
  }

  /**
   * Get a result from a call that might throw an exception. If the supplier finishes, the result is
   * returned. If the supplier continues to throw, when totalDuration has elapsed, this method will
   * throw that exception.
   *
   * @param supplier - code returning the result or throwing an exception
   * @param totalDuration - total amount of time to retry
   * @param sleepDuration - amount of time to sleep between retries
   * @param retryExceptionList - nullable; a list of exception classes. If null, any exception is
   *     retried
   * @param <T> - type of result
   * @return - result from supplier, if no exception
   * @throws InterruptedException if the sleep is interrupted
   */
  public static <T> T getWithRetryOnException(
      SupplierWithException<T> supplier,
      Duration totalDuration,
      Duration sleepDuration,
      @Nullable List<Class<? extends Exception>> retryExceptionList)
      throws Exception {

    T result;
    Instant endTime = Instant.now().plus(totalDuration);

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
        supplier, DEFAULT_RETRY_TOTAL_DURATION, DEFAULT_SLEEP_DURATION, null);
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
}
