package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.FlightMap;

public class TestMissingUndoFlight extends FlightGenerator {
  public TestMissingUndoFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    TestStep stepImpl = new TestStepImpl();
    createStep(stepImpl).missingUndo();
  }
}
