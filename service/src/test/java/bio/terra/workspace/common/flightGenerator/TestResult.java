package bio.terra.workspace.common.flightGenerator;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = TestResultImpl.class)
public interface TestResult {
  int getX();
}
