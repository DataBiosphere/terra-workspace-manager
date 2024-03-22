package bio.terra.workspace.common.flightGenerator;

import java.util.List;

public interface TestStep {
  @NoUndo
  TestResult increment(TestResult result);

  @NoUndo
  List<TestResult> incrementBoth(TestResult result, TestResult result2);

  default void missingUndo() {}

  @UndoMethod("doesNotExists")
  default void undoDoesNotExist() {}
}
