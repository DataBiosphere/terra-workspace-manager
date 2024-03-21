package bio.terra.workspace.service.policy;

import static java.time.Instant.now;

import bio.terra.policy.client.ApiException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.ProcessingException;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a clone of the TCL SamRetry. Since the definition of ApiException is different
 * (even though it is nearly identical), we cannot just use that code.
 */
public class TpsRetry {
  private static final Logger logger = LoggerFactory.getLogger(TpsRetry.class);

  // The retry function starts with INITIAL_WAIT between retries, and doubles that until it
  // reaches MAXIMUM_WAIT, after which all retries are MAXIMUM_WAIT apart.
  private static final Duration MAXIMUM_WAIT = Duration.ofSeconds(30);
  private static final Duration INITIAL_WAIT = Duration.ofSeconds(10);
  private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(300);

  // Tps calls which timeout will throw ApiExceptions wrapping SocketTimeoutExceptions and will have
  // an errorCode 0. This isn't a real HTTP status code, but we can check for it anyway.
  private static final int TIMEOUT_STATUS_CODE = 0;

  private final Instant operationTimeout;

  // How long to wait between retries.
  private Duration retryDuration;

  TpsRetry() {
    this.operationTimeout = now().plus(OPERATION_TIMEOUT);
    this.retryDuration = INITIAL_WAIT;
  }

  protected TpsRetry(Duration timeout) {
    this.operationTimeout = now().plus(timeout);
    this.retryDuration = INITIAL_WAIT;
  }

  @FunctionalInterface
  public interface TpsVoidFunction {
    void apply() throws ApiException, InterruptedException;
  }

  @FunctionalInterface
  public interface TpsFunction<R> {
    R apply() throws ApiException, InterruptedException;
  }

  /**
   * Requests made through the Tps client library sometimes fail with timeouts, generally due to
   * transient network or connection issues. When this happens, the client library will throw an API
   * exceptions with status code 0 wrapping a SocketTimeoutException. These errors should always be
   * retried.
   */
  public static boolean isTimeoutException(ApiException apiException) {
    return apiException.getCode() == TIMEOUT_STATUS_CODE
        && apiException.getCause() instanceof SocketTimeoutException;
  }

  public static <T> T retry(TpsRetry.TpsFunction<T> function)
      throws ApiException, InterruptedException {
    TpsRetry TpsRetry = new TpsRetry();
    return TpsRetry.perform(function);
  }

  public static <T> T retry(TpsRetry.TpsFunction<T> function, Duration timeout)
      throws ApiException, InterruptedException {
    TpsRetry TpsRetry = new TpsRetry(timeout);
    return TpsRetry.perform(function);
  }

  public static void retry(TpsRetry.TpsVoidFunction function)
      throws ApiException, InterruptedException {
    TpsRetry TpsRetry = new TpsRetry();
    TpsRetry.performVoid(function);
  }

  public static void retry(TpsRetry.TpsVoidFunction function, Duration timeout)
      throws ApiException, InterruptedException {
    TpsRetry TpsRetry = new TpsRetry(timeout);
    TpsRetry.performVoid(function);
  }

  private <T> T perform(TpsRetry.TpsFunction<T> function)
      throws ApiException, InterruptedException {
    while (true) {
      try {
        return function.apply();
      } catch (ApiException ex) {
        if (isRetryable(ex)) {
          logger.info("TpsRetry: caught retry-able exception: ", ex);
          sleepOrTimeoutBeforeRetrying(ex);
        } else {
          throw ex;
        }
      } catch (ProcessingException ws) {
        logger.info("TpsRetry: caught retry-able ProcessingException: ", ws);
        sleepOrTimeoutBeforeRetrying(new ApiException(ws));
      }
    }
  }

  private boolean isRetryable(ApiException apiException) {
    return isTimeoutException(apiException)
        || apiException.getCode() == HttpStatus.SC_FORBIDDEN
        || apiException.getCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR
        || apiException.getCode() == HttpStatus.SC_BAD_GATEWAY
        || apiException.getCode() == HttpStatus.SC_SERVICE_UNAVAILABLE
        || apiException.getCode() == HttpStatus.SC_GATEWAY_TIMEOUT;
  }

  private void performVoid(TpsRetry.TpsVoidFunction function)
      throws ApiException, InterruptedException {
    perform(
        () -> {
          function.apply();
          return null;
        });
  }

  /**
   * Given an exception from Tps, either timeout and rethrow the error from Tps or sleep for
   * retryDuration. If the thread times out while sleeping, throw the initial exception.
   *
   * <p>With the current values of INITIAL_WAIT and MAXIMUM_WAIT, this will sleep with the pattern
   * 10, 20, 30, 30, 30... seconds.
   *
   * @param previousException The error Tps threw
   * @throws ApiException InterruptedException
   */
  private void sleepOrTimeoutBeforeRetrying(ApiException previousException)
      throws ApiException, InterruptedException {
    if (operationTimeout.minus(retryDuration).isBefore(now())) {
      logger.error("TpsRetry: operation timed out after " + operationTimeout.toString());
      // If we timed out, throw the error from Tps that caused us to need to retry.
      throw previousException;
    }
    logger.info("TpsRetry: sleeping " + retryDuration.getSeconds() + " seconds");
    TimeUnit.SECONDS.sleep(retryDuration.getSeconds());
    retryDuration = retryDuration.plus(INITIAL_WAIT);
    if (retryDuration.compareTo(MAXIMUM_WAIT) > 0) {
      retryDuration = MAXIMUM_WAIT;
    }
  }
}
