package bio.terra.workspace.common.flightGenerator;

import java.util.Objects;

public class TestResultImpl implements TestResult {
  private int x;

  public TestResultImpl() {
    this.x = 0;
  }

  public TestResultImpl(int x) {
    this.x = x;
  }

  @Override
  public int getX() {
    return this.x;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TestResultImpl that = (TestResultImpl) o;
    return x == that.x;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x);
  }

  @Override
  public String toString() {
    return "TestResultImpl{" + "x=" + x + '}';
  }
}
