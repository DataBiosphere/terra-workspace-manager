package bio.terra.workspace.service.create.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.service.create.CreateDAO;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import org.springframework.context.ApplicationContext;

public class WorkspaceCreateFlight extends Flight {

  public WorkspaceCreateFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    CreateDAO createDao = (CreateDAO) appContext.getBean("createDAO");
    SamService iamClient = (SamService) appContext.getBean("samService");

    // get data from inputs that steps need
    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    addStep(new CreateWorkspaceStep(createDao));
    addStep(new CreateWorkspaceAuthzStep(iamClient, userReq));
  }
}
