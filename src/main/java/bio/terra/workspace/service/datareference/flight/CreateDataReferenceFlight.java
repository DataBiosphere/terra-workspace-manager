package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.db.DataReferenceDao;
import org.springframework.context.ApplicationContext;

public class CreateDataReferenceFlight extends Flight {

  public CreateDataReferenceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    DataReferenceDao dataReferenceDao = (DataReferenceDao) appContext.getBean("dataReferenceDao");

    addStep(new CreateDataReferenceStep(dataReferenceDao));
  }
}
