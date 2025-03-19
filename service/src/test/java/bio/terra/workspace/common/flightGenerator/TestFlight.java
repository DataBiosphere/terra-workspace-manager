package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.FlightMap;

/**
 * This is a test flight that demonstrates how to use the FlightGenerator to create a flight. The
 * flight is a simple sequence of steps that increment a value and then return the result. Each step
 * is created by the createStep method, which returns a proxy for the step. When a function is
 * called on a proxy, it records the function call and the arguments, adds a step implementation to
 * the flight then returns a proxy for the return value. The return value proxy knows which step it
 * is associated with, so steps can be chained together. The setResponse method is used to set the
 * response for the flight.
 */
public class TestFlight extends FlightGenerator {
  public static final String INITIAL_VALUE = "initialValue";

  public TestFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // note the type is the interface, not the implementation, createStep() won't work with the
    // implementation
    // note stepImpl is stateless, so we can reuse it
    TestStep stepImpl = new TestStepImpl();

    var initial = inputParameters.get(INITIAL_VALUE, TestResult.class);
    var step1Results = createStep(stepImpl).increment(initial);
    var step2Results = createStep(stepImpl).increment(step1Results);
    var step3Results = createStep(stepImpl).incrementBoth(step1Results, step2Results);
    setResponse(step3Results);
  }
}
