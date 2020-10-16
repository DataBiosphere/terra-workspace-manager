package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.WorkspaceProjectConfiguration;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import org.springframework.context.ApplicationContext;

public class WorkspaceCreateFlight extends Flight {
  // TODO(PF-152): Use real feature gating/input parameters.
  private static final boolean CREATE_PROJECT = false;

  public WorkspaceCreateFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    ApplicationContext appContext = (ApplicationContext) applicationContext;
    WorkspaceDao workspaceDao = (WorkspaceDao) appContext.getBean("workspaceDao");
    SamService iamClient = (SamService) appContext.getBean("samService");
    WorkspaceProjectConfiguration workspaceProjectConfiguration =
            appContext.getBean(WorkspaceProjectConfiguration.class);

    // get data from inputs that steps need
    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    addStep(new CreateWorkspaceAuthzStep(iamClient, userReq));
    addStep(new CreateWorkspaceStep(workspaceDao));
    if (CREATE_PROJECT) {
      addStep(new GenerateProjectIdStep());
      addStep(new CreateProjectStep(null, null, workspaceProjectConfiguration));
      addStep(new StoreGoogleCloudContextStep(workspaceDao));
    }
  }
}
