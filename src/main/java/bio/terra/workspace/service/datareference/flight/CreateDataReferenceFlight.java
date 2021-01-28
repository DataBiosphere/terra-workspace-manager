package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;

public class CreateDataReferenceFlight extends Flight {

  public CreateDataReferenceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    addStep(new GenerateReferenceIdStep());
    addStep(
        new CreateDataReferenceStep(
            appContext.getDataReferenceDao(), appContext.getObjectMapper()));
  }
}
