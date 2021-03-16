package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;

public class WorkspaceDeleteFlight extends Flight {
  private static final int INITIAL_INTERVALS_SECONDS = 1;
  private static final int MAX_INTERVAL_SECONDS = 8;
  private static final int MAX_OPERATION_TIME_SECONDS = 5 * 60;

  public WorkspaceDeleteFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    WorkspaceStage workspaceStage =
        inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, WorkspaceStage.class);
    // TODO: we still need the following steps once their features are supported:
    // 1. delete controlled resources using the Cloud Resource Manager library
    // 2. Notify all registered applications of deletion, once applications are supported
    // 3. Delete policy objects in Policy Manager, once it exists.

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            INITIAL_INTERVALS_SECONDS, MAX_INTERVAL_SECONDS, MAX_OPERATION_TIME_SECONDS);

    addStep(
        new DeleteProjectStep(appContext.getCrlService(), appContext.getWorkspaceDao()), retryRule);
    // Workspace authz is handled differently depending on whether WSM owns the underlying Sam
    // resource or not, as indicated by the workspace stage enum.
    switch (workspaceStage) {
      case MC_WORKSPACE:
        addStep(new DeleteWorkspaceAuthzStep(appContext.getSamService(), userReq));
        break;
      case RAWLS_WORKSPACE:
        // Do nothing, since WSM does not own the Sam resource.
        break;
      default:
        throw new InternalLogicException(
            "Unknown workspace stage during creation: " + workspaceStage.name());
    }
    addStep(new DeleteWorkspaceStateStep(appContext.getWorkspaceDao()));
  }
}
