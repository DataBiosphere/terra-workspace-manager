package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.db.DataReferenceDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;

public class CreateDataReferenceFlight extends Flight {

  public CreateDataReferenceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    DataReferenceDao dataReferenceDao = (DataReferenceDao) appContext.getBean("dataReferenceDao");
    ObjectMapper objectMapper = (ObjectMapper) appContext.getBean("objectMapper");

    addStep(new GenerateReferenceIdStep());
    addStep(new CreateDataReferenceStep(dataReferenceDao, objectMapper));
  }
}
