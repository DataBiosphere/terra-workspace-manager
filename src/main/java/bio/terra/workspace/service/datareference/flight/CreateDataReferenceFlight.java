package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightApplicationContext;

public class CreateDataReferenceFlight extends Flight {

  public CreateDataReferenceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightApplicationContext appContext =
        FlightApplicationContext.getFromObject(applicationContext);

    addStep(new GenerateReferenceIdStep());
    addStep(
        new CreateDataReferenceStep(
            appContext.getDataReferenceDao(), appContext.getObjectMapper()));
  }
}
