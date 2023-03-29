package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.service.resource.model.CloningInstructions.COPY_NOTHING;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;

/** Top-most flight for cloning a workspace. Launches sub-flights for most of the work. */
public class CloneWorkspaceFlight extends Flight {

  public CloneWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // Flight Map
    // 0. Clone all folders in the workspace
    // 1. Build a list of resources to clone and attach the updated cloned folder id
    // 2. Create job IDs for future sub-flights and a couple other things
    // 3. Launch a flight to create a cloud context if necessary
    // 3a. Await the context flight
    // TODO: [PF-1972] 4. Merge Policy Attributes
    // 5. Launch a flight to clone all resources on the list
    // 5a. Await the clone all resources flight and build a response
    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    var cloudRetryRule = RetryRules.cloud();
    var longCloudRetryRule = RetryRules.cloudLongRunning();

    var sourceWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    Workspace sourceWorkspace = flightBeanBag.getWorkspaceDao().getWorkspace(sourceWorkspaceId);

    addStep(
        new CloneAllFoldersStep(flightBeanBag.getSamService(), flightBeanBag.getFolderDao()),
        RetryRules.shortDatabase());

    addStep(new FindResourcesToCloneStep(flightBeanBag.getResourceDao()), cloudRetryRule);

    addStep(new CreateIdsForFutureStepsStep());

    // Only create a cloud context if the source workspace has a cloud context
    if (flightBeanBag
        .getGcpCloudContextService()
        .getGcpCloudContext(sourceWorkspaceId)
        .isPresent()) {
      addStep(
          new LaunchCreateCloudContextFlightStep(
              flightBeanBag.getWorkspaceService(),
              CloudPlatform.GCP,
              ControlledResourceKeys.CREATE_GCP_CLOUD_CONTEXT_FLIGHT_ID),
          cloudRetryRule);
      addStep(
          new AwaitCreateCloudContextFlightStep(
              ControlledResourceKeys.CREATE_GCP_CLOUD_CONTEXT_FLIGHT_ID),
          longCloudRetryRule);
    }

    if (flightBeanBag
        .getAzureCloudContextService()
        .getAzureCloudContext(sourceWorkspaceId)
        .isPresent()) {
      addStep(
          new LaunchCreateCloudContextFlightStep(
              flightBeanBag.getWorkspaceService(),
              CloudPlatform.AZURE,
              ControlledResourceKeys.CREATE_AZURE_CLOUD_CONTEXT_FLIGHT_ID),
          cloudRetryRule);
      addStep(
          new AwaitCreateCloudContextFlightStep(
              ControlledResourceKeys.CREATE_AZURE_CLOUD_CONTEXT_FLIGHT_ID),
          cloudRetryRule);
    }

    // If TPS is enabled, clone the policy attributes
    // We do not support policies on RAWLS stage workspaces
    if (flightBeanBag.getFeatureConfiguration().isTpsEnabled()
        && sourceWorkspace.getWorkspaceStage() != WorkspaceStage.RAWLS_WORKSPACE) {
      var destinationWorkspace =
          FlightUtils.getRequired(
              inputParameters, JobMapKeys.REQUEST.getKeyName(), Workspace.class);
      var userRequest =
          inputParameters.get(
              JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
      addStep(
          new MergePolicyAttributesStep(
              sourceWorkspaceId,
              destinationWorkspace.getWorkspaceId(),
              COPY_NOTHING,
              flightBeanBag.getTpsApiDispatch()),
          cloudRetryRule);
    }

    addStep(new LaunchCloneAllResourcesFlightStep(), cloudRetryRule);
    addStep(new AwaitCloneAllResourcesFlightStep(), longCloudRetryRule);
  }
}
