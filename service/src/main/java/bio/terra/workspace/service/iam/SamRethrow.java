package bio.terra.workspace.service.iam;

import bio.terra.common.sam.exception.SamExceptionFactory;

/**
 * When we use Sam in flights, we want to let InterruptedException fly and be caught by Stairway.
 * However, when we use Sam outside of flights, we want to propagate the Interrupted exception.
 * These static methods perform that propagation.
 */
public class SamRethrow {

  /**
   * For use with onInterrupted methods.
   *
   * @param <R>
   */
  @FunctionalInterface
  public interface InterruptedSupplier<R> {
    R apply() throws InterruptedException;
  }

  /** For use with onInterrupted. */
  public interface VoidInterruptedSupplier {
    void apply() throws InterruptedException;
  }

  /**
   * Use this function outside of Flights. In that case, we do not expect SamRetry to be interrupted
   * so the interruption is unexpected and should be surfaced all the way up as an unchecked
   * exception, which this function does.
   *
   * <p>Usage: SamRethrow.onInterrupted(() -> samService.isAuthorized(...), "isAuthorized")) {
   *
   * @param function The SamService function to call.
   * @param operation The name of the function to use in the error message.
   * @param <T> The return type of the function to call.
   * @return The return value of the function to call.
   */
  public static <T> T onInterrupted(InterruptedSupplier<T> function, String operation) {
    try {
      return function.apply();
    } catch (InterruptedException e) {
      throw SamExceptionFactory.create("Interrupted during Sam operation " + operation, e);
    }
  }

  /**
   * Like above, but for functions that return void.
   *
   * @param function The SamService function to call.
   * @param operation The name of the function to use in the error message.
   */
  public static void onInterrupted(VoidInterruptedSupplier function, String operation) {
    try {
      function.apply();
    } catch (InterruptedException e) {
      throw SamExceptionFactory.create("Interrupted during Sam operation " + operation, e);
    }
  }
}
