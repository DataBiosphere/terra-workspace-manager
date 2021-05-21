package bio.terra.workspace.common.utils;

import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.stairway.RetryRuleFixedInterval;

public class RetryRules {

  private RetryRules() {}

  /**
   * Retry rule for immediately retrying a failed step twice. This is useful for retrying operations
   * where waiting is not necessary, e.g. retrying conflicted SQL transactions.
   */
  public static RetryRule databaseConflict() {
    return new RetryRuleFixedInterval(0, 2);
  }

  /**
   * Retry rule for steps interacting with GCP. If GCP is down, we don't know when it will be back,
   * so don't wait forever. Note that RetryRules can be re-used within but not across Flight
   * instances.
   */
  public static RetryRule cloud() {
    return new RetryRuleFixedInterval(10, 10);
  }

  public static RetryRule cloudLongRunning() {
    return new RetryRuleExponentialBackoff(
        1, 8, 5 * 60);
  }

  public static RetryRule shortExponential() {
    return new RetryRuleExponentialBackoff(1, 8, 16);
  }

  /**
   * Retry rule for handling unexpected timeouts with Sam. Note that some errors from Sam (like
   * NOT_FOUND responses to resources which were already deleted) are not handled by retries.
   */
  public static RetryRule sam() {
    return new RetryRuleFixedInterval(10, 2);
  }

  private static final int BUFFER_INITIAL_INTERVAL_SECONDS = 1;
  private static final int BUFFER_MAX_INTERVAL_SECONDS = 5 * 60;
  private static final int BUFFER_MAX_OPERATION_TIME_SECONDS = 15 * 60;

  public static RetryRule buffer() {
    return new RetryRuleExponentialBackoff(
        BUFFER_INITIAL_INTERVAL_SECONDS,
        BUFFER_MAX_INTERVAL_SECONDS,
        BUFFER_MAX_OPERATION_TIME_SECONDS);
  }
}
