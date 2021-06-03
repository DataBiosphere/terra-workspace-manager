package bio.terra.workspace.db;

import bio.terra.common.db.DatabaseRetryUtils;
import bio.terra.common.db.DatabaseRetryUtils.DatabaseOperation;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.service.iam.SamService.InterruptedSupplier;
import bio.terra.workspace.service.iam.SamService.VoidInterruptedSupplier;
import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;

/**
 * Wrapper functions around common-libs database retry functions with pre-defined values for number
 * of attempts and backoff duration. These values were picked arbitrarily and may need tweaking in
 * the future.
 */
public class DbRetryUtils {

  private static final Duration RETRY_BACKOFF = Duration.ofMillis(250);
  @VisibleForTesting public static final int MAX_ATTEMPTS = 10;

  public static <T> T retry(DatabaseOperation<T> operation) throws InterruptedException {
    return DatabaseRetryUtils.executeAndRetry(operation, RETRY_BACKOFF, MAX_ATTEMPTS);
  }

  public static void retry(Runnable operation) throws InterruptedException {
    DatabaseOperation<Void> dbOperation =
        () -> {
          operation.run();
          return null;
        };
    DatabaseRetryUtils.executeAndRetry(dbOperation, RETRY_BACKOFF, MAX_ATTEMPTS);
  }

  public static <T> T throwIfInterrupted(InterruptedSupplier<T> operation) {
    try {
      return operation.apply();
    } catch (InterruptedException e) {
      throw new InternalServerErrorException("Interrupted during database operation");
    }
  }

  public static void throwIfInterrupted(VoidInterruptedSupplier operation) {
    try {
      operation.apply();
    } catch (InterruptedException e) {
      throw new InternalServerErrorException("Interrupted during database operation");
    }
  }
}
