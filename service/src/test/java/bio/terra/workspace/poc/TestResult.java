package bio.terra.workspace.poc;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = TestResultImpl.class)
public interface TestResult {
  int getX();
}
