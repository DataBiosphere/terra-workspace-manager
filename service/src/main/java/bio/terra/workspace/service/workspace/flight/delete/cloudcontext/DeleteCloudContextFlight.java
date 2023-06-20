package bio.terra.workspace.service.workspace.flight.delete.cloudcontext;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.workspace.CloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

public class DeleteCloudContextFlight extends Flight {
  // addStep is protected in Flight, so make an override that is public
  @Override
  public void addStep(Step step, RetryRule retryRule) {
    super.addStep(step, retryRule);
  }

  @Override
  public void addStep(Step step) {
    super.addStep(step);
  }

  public DeleteCloudContextFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);

    UUID workspaceUuid =
        UUID.fromString(
            FlightUtils.getRequired(
                inputParameters, WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        FlightUtils.getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);
    CloudPlatform cloudPlatform =
        FlightUtils.getRequired(
            inputParameters, WorkspaceFlightMapKeys.CLOUD_PLATFORM, CloudPlatform.class);
    WorkspaceDao workspaceDao = appContext.getWorkspaceDao();
    SamService samService = appContext.getSamService();
    CloudContextService cloudContextService = cloudPlatform.getCloudContextService();
    ControlledResourceService controlledResourceService = appContext.getControlledResourceService();
    ResourceDao resourceDao = appContext.getResourceDao();

    RetryRule dbRetry = RetryRules.shortDatabase();
    RetryRule serviceRetry = RetryRules.cloud();

    addStep(new DeleteCloudContextStartStep(workspaceUuid, workspaceDao, cloudPlatform), dbRetry);

    addStep(
        new BuildAndValidateResourceListStep(
            cloudContextService, samService, userRequest, workspaceUuid),
        serviceRetry);

    addStep(
        new DeleteResourcesStep(
            cloudContextService,
            controlledResourceService,
            userRequest,
            resourceDao,
            workspaceUuid),
        serviceRetry);

    // Add the delete steps for the appropriate cloud type
    cloudContextService.addDeleteCloudContextSteps(this, appContext, workspaceUuid, userRequest);

    addStep(
        new DeleteCloudContextFinishStep(
            userRequest,
            workspaceUuid,
            workspaceDao,
            cloudPlatform,
            appContext.getWorkspaceActivityLogService()),
        dbRetry);
  }
}
