package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.flight.clone.ClonePolicyAttributesStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;

/** Top-most flight for cloning a GCP workspace. Launches sub-flights for most of the work. */
public class CloneGcpWorkspaceFlight extends Flight {

  public CloneGcpWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // Flight Map
    // 0. Clone all folders in the workspace
    // 1. Build a list of resources to clone and attach the updated cloned folder id
    // 2. Create job IDs for future sub-flights and a couple other things
    // 3. Launch a flight to create the GCP cloud context
    // 3a. Await the context flight
    // TODO: [PF-1972] 4. Merge Policy Attributes
    // 5. Launch a flight to clone all resources on the list
    // 5a. Await the clone all resources flight and build a response
    // 6. Build a list of enabled applications
    // 6a. Launch a flight to enable those applications in destination workspace
    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    var cloudRetryRule = RetryRules.cloud();
    var longCloudRetryRule = RetryRules.cloudLongRunning();

    var sourceWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    Workspace sourceWorkspace = flightBeanBag.getWorkspaceDao().getWorkspace(sourceWorkspaceId);

    addStep(new CloneAllFoldersStep(flightBeanBag.getFolderDao()));

    addStep(new FindResourcesToCloneStep(flightBeanBag.getResourceDao()), cloudRetryRule);

    addStep(new CreateIdsForFutureStepsStep());

    // Only create a GCP cloud context if the source workspace has a GCP cloud context
    if (flightBeanBag
        .getGcpCloudContextService()
        .getGcpCloudContext(sourceWorkspaceId)
        .isPresent()) {
      addStep(
          new LaunchCreateGcpContextFlightStep(flightBeanBag.getWorkspaceService()),
          RetryRules.cloud());
      addStep(new AwaitCreateGcpContextFlightStep(), longCloudRetryRule);
    }

    // If TPS is enabled, clone the policy attributes
    // We do not support policies on RAWLS stage workspaces
    if (flightBeanBag.getFeatureConfiguration().isTpsEnabled()
        && sourceWorkspace.getWorkspaceStage() != WorkspaceStage.RAWLS_WORKSPACE) {
      var destinationWorkspace =
          inputParameters.get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);
      var userRequest =
          inputParameters.get(
              JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
      addStep(
          new ClonePolicyAttributesStep(
              sourceWorkspaceId,
              destinationWorkspace.getWorkspaceId(),
              userRequest,
              flightBeanBag.getTpsApiDispatch()),
          cloudRetryRule);
    }

    addStep(new LaunchCloneAllResourcesFlightStep(), cloudRetryRule);
    addStep(new AwaitCloneAllResourcesFlightStep(), longCloudRetryRule);

    addStep(new FindEnabledApplicationsStep(flightBeanBag.getApplicationDao()), cloudRetryRule);
    addStep(new LaunchEnableApplicationsFlightStep(), cloudRetryRule);
  }
}
