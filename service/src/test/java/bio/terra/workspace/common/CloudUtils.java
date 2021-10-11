package bio.terra.workspace.common;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Static utility functions useful for working with cloud objects. */
public class CloudUtils {

  private static final Logger logger = LoggerFactory.getLogger(CloudUtils.class);

  @FunctionalInterface
  public interface SupplierWithException<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  public interface RunnableWithException {
    void run() throws Exception;
  }

  /**
   * Get a result from a call that might throw an exception. Treat the exception as retryable, sleep
   * for 15 seconds, and retry up to 40 times. This structure is useful for situations where we are
   * waiting on a cloud IAM permission change to take effect.
   *
   * @param supplier - code returning the result or throwing an exception
   * @param <T> - type of result
   * @return - result from supplier, the first time it doesn't throw, or null if all tries have been
   *     exhausted
   * @throws InterruptedException
   */
  public static @Nullable <T> T getWithRetryOnException(SupplierWithException<T> supplier)
      throws InterruptedException {
    T result = null;
    int numTries = 40;
    Duration sleepDuration = Duration.ofSeconds(15);
    while (numTries > 0) {
      try {
        result = supplier.get();
        break;
      } catch (Exception e) {
        numTries--;
        logger.info(
            "Exception \"{}\". Waiting {} seconds for permissions to propagate. Tries remaining: {}",
            e.getMessage(),
            sleepDuration.toSeconds(),
            numTries);
        TimeUnit.MILLISECONDS.sleep(sleepDuration.toMillis());
      }
    }
    return result;
  }

  public static boolean runWithRetryOnException(RunnableWithException fn)
      throws InterruptedException {
    return getWithRetryOnException(
        () -> {
          fn.run();
          return true;
        });
  }
}
