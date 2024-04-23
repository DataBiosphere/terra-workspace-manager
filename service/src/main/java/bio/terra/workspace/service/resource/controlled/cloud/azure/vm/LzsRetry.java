package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static java.time.Instant.now;

import bio.terra.lz.futureservice.client.ApiException;
import jakarta.ws.rs.ProcessingException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a clone of the TCL SamRetry. Since the definition of ApiException is different
 * (even though it is nearly identical), we cannot just use that code.
 */
public class LzsRetry {
  private static final Logger logger = LoggerFactory.getLogger(LzsRetry.class);

  // The retry function starts with INITIAL_WAIT between retries, and doubles that until it
  // reaches MAXIMUM_WAIT, after which all retries are MAXIMUM_WAIT apart.
  private static final Duration MAXIMUM_WAIT = Duration.ofSeconds(30);
  private static final Duration INITIAL_WAIT = Duration.ofSeconds(10);
  private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(300);

  // LZS calls which timeout will throw ApiExceptions wrapping SocketTimeoutExceptions and will have
  // an errorCode 0. This isn't a real HTTP status code, but we can check for it anyway.
  private static final int TIMEOUT_STATUS_CODE = 0;

  private final Instant operationTimeout;

  // How long to wait between retries.
  private Duration retryDuration;

  LzsRetry() {
    this.operationTimeout = now().plus(OPERATION_TIMEOUT);
    this.retryDuration = INITIAL_WAIT;
  }

  protected LzsRetry(Duration timeout) {
    this.operationTimeout = now().plus(timeout);
    this.retryDuration = INITIAL_WAIT;
  }

  @FunctionalInterface
  public interface LzsVoidFunction {
    void apply() throws ApiException, InterruptedException;
  }

  @FunctionalInterface
  public interface LzsFunction<R> {
    R apply() throws ApiException, InterruptedException;
  }

  /**
   * Requests made through the LZS client library sometimes fail with timeouts, generally due to
   * transient network or connection issues. When this happens, the client library will throw an API
   * exceptions with status code 0 wrapping a SocketTimeoutException. These errors should always be
   * retried.
   */
  public static boolean isTimeoutException(ApiException apiException) {
    return apiException.getCode() == TIMEOUT_STATUS_CODE
        && apiException.getCause() instanceof SocketTimeoutException;
  }

  public static <T> T retry(LzsRetry.LzsFunction<T> function)
      throws ApiException, InterruptedException {
    LzsRetry LzsRetry = new LzsRetry();
    return LzsRetry.perform(function);
  }

  public static <T> T retry(LzsRetry.LzsFunction<T> function, Duration timeout)
      throws ApiException, InterruptedException {
    LzsRetry LzsRetry = new LzsRetry(timeout);
    return LzsRetry.perform(function);
  }

  public static void retry(LzsRetry.LzsVoidFunction function)
      throws ApiException, InterruptedException {
    LzsRetry LzsRetry = new LzsRetry();
    LzsRetry.performVoid(function);
  }

  public static void retry(LzsRetry.LzsVoidFunction function, Duration timeout)
      throws ApiException, InterruptedException {
    LzsRetry LzsRetry = new LzsRetry(timeout);
    LzsRetry.performVoid(function);
  }

  private <T> T perform(LzsRetry.LzsFunction<T> function)
      throws ApiException, InterruptedException {
    while (true) {
      try {
        return function.apply();
      } catch (ApiException ex) {
        if (isRetryable(ex)) {
          logger.info("LzsRetry: caught retry-able exception: ", ex);
          sleepOrTimeoutBeforeRetrying(ex);
        } else {
          throw ex;
        }
      } catch (ProcessingException ws) {
        logger.info("LzsRetry: caught retry-able ProcessingException: ", ws);
        sleepOrTimeoutBeforeRetrying(ws);
      }
    }
  }

  private boolean isRetryable(ApiException apiException) {
    return isTimeoutException(apiException)
        || apiException.getCode() == HttpStatus.SC_FORBIDDEN
        // || apiException.getCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR
        || apiException.getCode() == HttpStatus.SC_BAD_GATEWAY
        || apiException.getCode() == HttpStatus.SC_SERVICE_UNAVAILABLE
        || apiException.getCode() == HttpStatus.SC_GATEWAY_TIMEOUT;
  }

  private void performVoid(LzsRetry.LzsVoidFunction function)
      throws ApiException, InterruptedException {
    perform(
        () -> {
          function.apply();
          return null;
        });
  }

  /**
   * Given an exception from LZS, either timeout and rethrow the error from LZS or sleep for
   * retryDuration. If the thread times out while sleeping, throw the initial exception.
   *
   * <p>With the current values of INITIAL_WAIT and MAXIMUM_WAIT, this will sleep with the pattern
   * 10, 20, 30, 30, 30... seconds.
   *
   * @param previousException The error LZS threw
   * @throws E, InterruptedException
   */
  private <E extends Exception> void sleepOrTimeoutBeforeRetrying(E previousException)
      throws E, InterruptedException {
    if (operationTimeout.minus(retryDuration).isBefore(now())) {
      logger.error("LzsRetry: operation timed out after " + operationTimeout);
      // If we timed out, throw the error from LZS that caused us to need to retry.
      throw previousException;
    }
    logger.info("LzsRetry: sleeping " + retryDuration.getSeconds() + " seconds");
    TimeUnit.SECONDS.sleep(retryDuration.getSeconds());
    retryDuration = retryDuration.plus(INITIAL_WAIT);
    if (retryDuration.compareTo(MAXIMUM_WAIT) > 0) {
      retryDuration = MAXIMUM_WAIT;
    }
  }
}
