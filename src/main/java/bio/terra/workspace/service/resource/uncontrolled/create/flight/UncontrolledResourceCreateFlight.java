package bio.terra.workspace.service.resource.uncontrolled.create.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.db.UncontrolledResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import org.springframework.context.ApplicationContext;

public class UncontrolledResourceCreateFlight extends Flight {

  public UncontrolledResourceCreateFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    UncontrolledResourceDao uncontrolledResourceDao =
        (UncontrolledResourceDao) appContext.getBean("uncontrolledResourceDao");
    SamService iamClient = (SamService) appContext.getBean("samService");

    // get data from inputs that steps need
    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    addStep(new CreateUncontrolledResourceStep(uncontrolledResourceDao));
  }
}
