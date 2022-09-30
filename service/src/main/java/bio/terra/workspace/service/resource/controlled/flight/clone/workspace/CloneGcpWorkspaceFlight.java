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
import java.util.UUID;

/** Top-most flight for cloning a GCP workspace. Launches sub-flights for most of the work. */
public class CloneGcpWorkspaceFlight extends Flight {

  public CloneGcpWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // Flight Map
    // 0. Build a list of resources to clone
    // 1. Create job IDs for future sub-flights and a couple other things
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
    addStep(new FindResourcesToCloneStep(flightBeanBag.getResourceDao()), cloudRetryRule);

    addStep(new CreateIdsForFutureStepsStep());

    addStep(
        new LaunchCreateGcpContextFlightStep(flightBeanBag.getWorkspaceService()),
        RetryRules.cloud());
    addStep(new AwaitCreateGcpContextFlightStep(), longCloudRetryRule);

    // If TPS is enabled, clone the policy attributes
    if (flightBeanBag.getFeatureConfiguration().isTpsEnabled()) {
      var destinationWorkspace =
          inputParameters.get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);
      var sourceWorkspaceId =
          inputParameters.get(
              WorkspaceFlightMapKeys.ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
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
