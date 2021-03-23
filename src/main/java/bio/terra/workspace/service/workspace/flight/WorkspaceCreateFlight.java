package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;

public class WorkspaceCreateFlight extends Flight {

  public WorkspaceCreateFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    // get data from inputs that steps need
    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    WorkspaceStage workspaceStage =
        inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.class);

    // Workspace authz is handled differently depending on whether WSM owns the underlying Sam
    // resource or not, as indicated by the workspace stage enum.
    switch (workspaceStage) {
      case MC_WORKSPACE:
        addStep(new CreateWorkspaceAuthzStep(appContext.getSamService(), userReq));
        break;
      case RAWLS_WORKSPACE:
        addStep(new CheckSamWorkspaceAuthzStep(appContext.getSamService(), userReq));
        break;
      default:
        throw new InternalLogicException(
            "Unknown workspace stage during creation: " + workspaceStage.name());
    }
    addStep(new CreateWorkspaceStep(appContext.getWorkspaceDao()));
  }
}
