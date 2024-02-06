package bio.terra.workspace.poc;

import java.util.List;

public interface TestStep {
  TestResult increment(TestResult result);
  default void undo_increment(TestResult result) {}
  List<TestResult> incrementBoth(TestResult result, TestResult result2);
  default void undo_incrementBoth(TestResult result, TestResult result2) {}
}
