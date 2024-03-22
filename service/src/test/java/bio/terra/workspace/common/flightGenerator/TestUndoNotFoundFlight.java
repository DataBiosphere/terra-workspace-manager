package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.FlightMap;

public class TestUndoNotFoundFlight extends FlightGenerator {
  public TestUndoNotFoundFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    TestStep stepImpl = new TestStepImpl();
    createStep(stepImpl).undoDoesNotExist();
  }
}
