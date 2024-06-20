package bio.terra.workspace.common.flightGenerator;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;

/** This is a simple 1-step flight that uses a spring bean to do something. */
public class TestUndoFlight extends FlightGenerator {
  public TestUndoFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    var bean = FlightBeanBag.getFromObject(applicationContext).getBean(MySpringBean.class);

    createStep(bean).throwException();
  }
}
