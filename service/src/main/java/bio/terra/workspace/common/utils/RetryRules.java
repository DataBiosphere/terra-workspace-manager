package bio.terra.workspace.common.utils;

import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.stairway.RetryRuleFixedInterval;
import java.util.concurrent.TimeUnit;

/**
 * A selection of retry rule instantiators for use with Stairway flight steps. Each static method
 * creates a new object each time to prevent leakage across flights. Note that RetryRules can be
 * re-used within but not across Flight instances.
 */
public class RetryRules {

  private RetryRules() {}

  /**
   * Retry rule for steps interacting with GCP. If GCP is down, we don't know when it will be back,
   * so don't wait forever.
   */
  public static RetryRule cloud() {
    return new RetryRuleFixedInterval(10, 10);
  }

  /** Use for cloud operations that may take a couple of minutes to respond. */
  public static RetryRule cloudLongRunning() {
    return new RetryRuleExponentialBackoff(1, 8, 5 * 60);
  }

  /**
   * Use for a short exponential backoff retry, for operations that should be completable within a
   * few seconds.
   */
  public static RetryRule shortExponential() {
    // maxOperationTimeSeconds must be larger than socket timeout (20s), otherwise a socket timeout
    // won't be retried.
    return new RetryRuleExponentialBackoff(1, 8, /* maxOperationTimeSeconds */ 30);
  }

  /**
   * Buffer Retry rule settings. For Buffer Service, allow for long wait times. If the pool is
   * empty, Buffer Service may need time to actually create a new project.
   */
  public static RetryRule buffer() {
    return new RetryRuleExponentialBackoff(1, 5 * 60, 15 * 60);
  }

  /** Use for short database operations which may fail due to transaction conflicts. */
  public static RetryRule shortDatabase() {
    return new RetryRuleFixedInterval(/* intervalSeconds= */ 1, /* maxCount= */ 5);
  }

  private static class LongSyncRetryRule implements RetryRule {
    private static final int SHORT_INTERVAL_COUNT = 6;
    private static final int SHORT_INTERVAL_SECONDS = 10;
    private static final int LONG_INTERVAL_COUNT = 16;
    private static final int LONG_INTERVAL_SECONDS = 30;
    private int shortIntervalCounter;
    private int longIntervalCounter;

    @Override
    public void initialize() {
      shortIntervalCounter = 0;
      longIntervalCounter = 0;
    }

    @Override
    public boolean retrySleep() throws InterruptedException {
      if (shortIntervalCounter < SHORT_INTERVAL_COUNT) {
        shortIntervalCounter++;
        TimeUnit.SECONDS.sleep(SHORT_INTERVAL_SECONDS);
        return true;
      }
      if (longIntervalCounter < LONG_INTERVAL_COUNT) {
        longIntervalCounter++;
        TimeUnit.SECONDS.sleep(LONG_INTERVAL_SECONDS);
        return true;
      }
      return false;
    }
  }

  /**
   * Special retry rule for the instance permission sync. We are seeing very long propagation times
   * in environments with Domain Restricted Sharing. This rule tries not to penalize non-DRS
   * environments by running an initial phase of retries with the usual "cloud" interval of 10
   * seconds. Then after a minute it switches to a longer interval. Currently set to wait 8 more
   * minutes.
   */
  public static RetryRule longSync() {
    return new LongSyncRetryRule();
  }
}
