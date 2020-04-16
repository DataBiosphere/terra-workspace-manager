package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import org.springframework.context.ApplicationContext;

public class DataReferenceCreateFlight extends Flight {

  public DataReferenceCreateFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    DataReferenceDao dataReferenceDao = (DataReferenceDao) appContext.getBean("dataReferenceDao");
    SamService iamClient = (SamService) appContext.getBean("samService");

    // get data from inputs that steps need
    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    addStep(new CreateDataReferenceStep(dataReferenceDao));
  }
}
