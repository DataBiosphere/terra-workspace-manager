package bio.terra.workspace.poc;

import java.util.ArrayList;
import java.util.List;

public class TestStepImpl implements TestStep {

  @Override
  public TestResult increment(TestResult result) {
    return new TestResultImpl(result.getX() + 1);
  }

  @Override
  public List<TestResult> incrementBoth(TestResult result, TestResult result2) {
    return new ArrayList<>(List.of(increment(result), increment(result2)));
  }
}
