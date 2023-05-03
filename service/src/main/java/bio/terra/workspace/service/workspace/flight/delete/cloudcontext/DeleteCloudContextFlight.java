package bio.terra.workspace.service.workspace.flight.delete.cloudcontext;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.CloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFinishStep;
import bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextStartStep;
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
    WsmResourceStateRule wsmResourceStateRule = appContext.getFeatureConfiguration().getStateRule();
    WorkspaceDao workspaceDao = appContext.getWorkspaceDao();

    addStep(
        new DeleteCloudContextStartStep(workspaceUuid, workspaceDao, cloudPlatform));

    // Add the delete steps for the appropriate cloud type
    CloudContextService cloudContextService = cloudPlatform.getCloudContextService();
    cloudContextService.addDeleteCloudContextSteps(this, appContext, workspaceUuid, userRequest);

    addStep(new DeleteCloudContextFinishStep(workspaceUuid, workspaceDao, cloudPlatform));
  }
}
