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
import java.util.UUID;

// TODO(PF-555): There is a race condition if this flight runs at the same time as new controlled
//  resource creation, which may leak resources in Sam. Workspace locking would solve this issue.
public class WorkspaceDeleteFlight extends Flight {

  public WorkspaceDeleteFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    AuthenticatedUserRequest userReq =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    WorkspaceStage workspaceStage =
        WorkspaceStage.valueOf(
            inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, String.class));
    UUID workspaceId =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    // TODO: we still need the following steps once their features are supported:
    // 1. Delete the cloud contexts from non-GCP cloud platforms
    // 2. Notify all registered applications of deletion, once applications are supported
    // 3. Delete policy objects in Policy Manager, once it exists.

    RetryRule retryRule = RetryRules.cloudLongRunning();

    // We delete controlled resources from the Sam, but do not need to explicitly delete the
    // actual cloud objects or entries in WSM DB. GCP handles the cleanup when we delete the
    // containing project, and we cascade workspace deletion to resources in the DB.
    addStep(
        new DeleteControlledSamResourcesStep(
            appContext.getSamService(),
            appContext.getResourceDao(),
            workspaceId,
            /* cloudPlatform= */ null,
            userReq),
        retryRule);
    addStep(
        new DeleteProjectStep(appContext.getCrlService(), appContext.getWorkspaceDao()), retryRule);
    // Workspace authz is handled differently depending on whether WSM owns the underlying Sam
    // resource or not, as indicated by the workspace stage enum.
    switch (workspaceStage) {
      case MC_WORKSPACE:
        addStep(
            new DeleteWorkspaceAuthzStep(appContext.getSamService(), userReq, workspaceId),
            retryRule);
        break;
      case RAWLS_WORKSPACE:
        // Do nothing, since WSM does not own the Sam resource.
        break;
      default:
        throw new InternalLogicException(
            "Unknown workspace stage during deletion: " + workspaceStage.name());
    }
    addStep(new DeleteWorkspaceStateStep(appContext.getWorkspaceDao(), workspaceId), retryRule);
  }
}
