package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.model.Workspace;

public class WorkspaceCreateFlight extends Flight {

  public WorkspaceCreateFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    // get data from inputs that steps need
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    Workspace workspace = inputParameters.get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);

    // Workspace authz is handled differently depending on whether WSM owns the underlying Sam
    // resource or not, as indicated by the workspace stage enum.
    switch (workspace.getWorkspaceStage()) {
      case MC_WORKSPACE:
        addStep(
            new CreateWorkspaceAuthzStep(workspace, appContext.getSamService(), userRequest),
            RetryRules.shortExponential());
        break;
      case RAWLS_WORKSPACE:
        addStep(
            new CheckSamWorkspaceAuthzStep(workspace, appContext.getSamService(), userRequest),
            RetryRules.shortExponential());
        break;
      default:
        throw new InternalLogicException(
            "Unknown workspace stage during creation: " + workspace.getWorkspaceStage().name());
    }
    addStep(
        new CreateWorkspaceStep(workspace, appContext.getWorkspaceDao()),
        RetryRules.shortDatabase());
  }
}
