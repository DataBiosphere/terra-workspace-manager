package bio.terra.workspace.poc;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.poc.flightGenerator.FlightGenerator;

/**
 * This is a simple 1-step flight that uses a spring bean to do something.
 */
public class TestSpringBeanFlight extends FlightGenerator {
  public TestSpringBeanFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    var bean = FlightBeanBag.getFromObject(applicationContext).getBean(MySpringBean.class);

    createStep(bean).doSomething();
  }
}
