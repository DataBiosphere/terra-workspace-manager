package bio.terra.workspace.service.create.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import org.springframework.context.ApplicationContext;

public class WorkspaceCreateFlight extends Flight {

  public WorkspaceCreateFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    WorkspaceDao workspaceDao = (WorkspaceDao) appContext.getBean("workspaceDao");
    SamService iamClient = (SamService) appContext.getBean("samService");

    // get data from inputs that steps need
    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    addStep(new CreateWorkspaceStep(workspaceDao));
    addStep(new CreateWorkspaceAuthzStep(iamClient, userReq));
  }
}
