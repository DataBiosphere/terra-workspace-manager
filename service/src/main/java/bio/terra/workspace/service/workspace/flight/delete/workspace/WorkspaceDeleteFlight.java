package bio.terra.workspace.service.workspace.flight.delete.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;

// TODO(PF-555): There is a race condition if this flight runs at the same time as new controlled
//  resource or cloud context creation, which may leak resources. Some form of shared lock would
//  prevent this.
public class WorkspaceDeleteFlight extends Flight {

  public WorkspaceDeleteFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    UUID workspaceUuid =
        UUID.fromString(
            FlightUtils.getRequired(
                inputParameters, WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));

    RetryRule terraRetryRule = RetryRules.shortExponential();
    RetryRule dbRetryRule = RetryRules.shortDatabase();

    WorkspaceDao workspaceDao = appContext.getWorkspaceDao();

    addStep(new DeleteWorkspaceStartStep(workspaceUuid, workspaceDao), dbRetryRule);

    // For each cloud context in the workspace, run the cloud context delete flight
    for (CloudPlatform cloudPlatform : workspaceDao.listCloudPlatforms(workspaceUuid)) {
      String flightId = UUID.randomUUID().toString();
      addStep(
          new RunDeleteCloudContextFlightStep(workspaceUuid, cloudPlatform, userRequest, flightId));
    }

    // Belt and suspenders: the delete cloud context flights should fail and cause this flight to
    // fail if there are any resources left. However, just to be sure...
    addStep(
        new EnsureNoWorkspaceChildrenStep(appContext.getSamService(), userRequest, workspaceUuid),
        terraRetryRule);

    // Workspace-stage driven steps
    addAuthZSteps(appContext, inputParameters, userRequest, workspaceUuid, terraRetryRule);

    // Remove the workspace row
    addStep(new DeleteWorkspaceFinishStep(workspaceUuid, workspaceDao), dbRetryRule);
  }

  /**
   * Adds steps to delete sam resources owned by WSM. Workspace authz is handled differently
   * depending on whether WSM owns the underlying Sam resource or not, as indicated by the workspace
   * stage enum.
   */
  private void addAuthZSteps(
      FlightBeanBag context,
      FlightMap parameters,
      AuthenticatedUserRequest request,
      UUID workspaceId,
      RetryRule retryRule) {

    WorkspaceStage stage =
        WorkspaceStage.valueOf(
            parameters.get(WorkspaceFlightMapKeys.WORKSPACE_STAGE, String.class));

    switch (stage) {
      case MC_WORKSPACE:
        if (context.getFeatureConfiguration().isTpsEnabled()) {
          addStep(
              new DeleteWorkspacePoliciesStep(context.getTpsApiDispatch(), request, workspaceId),
              retryRule);
        }
        addStep(
            new DeleteWorkspaceAuthzStep(context.getSamService(), request, workspaceId), retryRule);
        break;
      case RAWLS_WORKSPACE:
        // Do nothing, since WSM does not own the Sam resource.
        break;
      default:
        throw new InternalLogicException(
            "Unknown workspace stage during deletion: " + stage.name());
    }
  }
}
