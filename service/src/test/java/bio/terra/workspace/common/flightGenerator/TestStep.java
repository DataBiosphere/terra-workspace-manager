package bio.terra.workspace.common.flightGenerator;

import java.util.List;

public interface TestStep {
  @NoUndo
  @NoRetry
  TestResult increment(TestResult result);

  @NoUndo
  @NoRetry
  List<TestResult> incrementBoth(TestResult result, TestResult result2);

  @NoRetry
  default void missingUndo() {}

  @UndoMethod("doesNotExists")
  @NoRetry
  default void undoDoesNotExist() {}

  @NoUndo
  @FixedIntervalRetry(intervalSeconds = 1, maxCount = 1)
  default void fixedRetryMethod() {}

  @NoUndo
  @ExponentialBackoffRetry(
      initialIntervalSeconds = 1,
      maxIntervalSeconds = 1,
      maxOperationTimeSeconds = 1)
  default void exponentialRetryMethod() {}

  @NoUndo
  @RandomBackoffRetry(operationIncrementMilliseconds = 1, maxConcurrency = 1, maxCount = 1)
  default void randomRetryMethod() {}

  @NoUndo
  @NoRetry
  default void noRetryMethod() {}

  @NoUndo
  @CustomRetry
  default void customRetryMethod() {}

  @NoUndo
  @FixedIntervalRetry(intervalSeconds = 1, maxCount = 1)
  @CustomRetry
  default void tooManyRetryMethod() {}

  @NoUndo
  default void missingRetryMethod() {}
}
