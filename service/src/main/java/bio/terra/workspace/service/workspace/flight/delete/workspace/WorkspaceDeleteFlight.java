package bio.terra.workspace.service.workspace.flight.delete.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.cloud.any.DeleteControlledDbResourcesStep;
import bio.terra.workspace.service.workspace.flight.cloud.any.DeleteControlledSamResourcesStep;
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
    ResourceDao resourceDao = appContext.getResourceDao();
    SamService samService = appContext.getSamService();

    addStep(new DeleteWorkspaceStartStep(workspaceUuid, workspaceDao), dbRetryRule);

    addStep(new MakeFlightIdsStep(workspaceUuid, workspaceDao));

    // For each cloud context in the workspace, run the cloud context delete flight
    for (CloudPlatform cloudPlatform : workspaceDao.listCloudPlatforms(workspaceUuid)) {
      addStep(
          new RunDeleteCloudContextFlightStep(workspaceUuid, cloudPlatform, userRequest),
          dbRetryRule);
    }

    // Delete all ANY controlled resources (right now, that means FlexResource). The only case
    // has no cloud resource, so we delete Sam and Db resources.
    addStep(
        new DeleteControlledSamResourcesStep(
            samService, resourceDao, workspaceUuid, CloudPlatform.ANY),
        terraRetryRule);
    addStep(
        new DeleteControlledDbResourcesStep(resourceDao, workspaceUuid, CloudPlatform.ANY),
        dbRetryRule);

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
    if (context.getFeatureConfiguration().isTpsEnabled()) {
      addStep(
          new DeleteWorkspacePoliciesStep(context.getTpsApiDispatch(), request, workspaceId),
          retryRule);
    }
    switch (stage) {
      case MC_WORKSPACE:
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
