package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;

public class WorkspaceDeleteFlight extends Flight {

  public WorkspaceDeleteFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    WorkspaceStage workspaceStage =
        WorkspaceStage.valueOf(
            inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, String.class));
    // TODO: we still need the following steps once their features are supported:
    // 1. delete controlled resources using the Cloud Resource Manager library
    // 2. Notify all registered applications of deletion, once applications are supported
    // 3. Delete policy objects in Policy Manager, once it exists.

    RetryRule retryRule = RetryRules.cloudLongRunning();

    addStep(
        new DeleteProjectStep(appContext.getCrlService(), appContext.getWorkspaceDao()), retryRule);
    // Workspace authz is handled differently depending on whether WSM owns the underlying Sam
    // resource or not, as indicated by the workspace stage enum.
    switch (workspaceStage) {
      case MC_WORKSPACE:
        addStep(new DeleteWorkspaceAuthzStep(appContext.getSamService(), userReq), retryRule);
        break;
      case RAWLS_WORKSPACE:
        // Do nothing, since WSM does not own the Sam resource.
        break;
      default:
        throw new InternalLogicException(
            "Unknown workspace stage during creation: " + workspaceStage.name());
    }
    addStep(new DeleteWorkspaceStateStep(appContext.getWorkspaceDao()), retryRule);
  }
}
