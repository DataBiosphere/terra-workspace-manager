package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;

/** Top-most flight for cloning a GCP workspace. Launches sub-flights for most of the work. */
public class CloneGcpWorkspaceFlight extends Flight {

  public CloneGcpWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // Flight Map
    // 0. Build a list of resources to clone
    // 1. Create job IDs for future sub-flights and a couple other things
    // 3. Launch a flight to create the GCP cloud context
    // 3a. Await the context flight
    // 4. Clone Policy Attributes
    // 5. Launch a flight to clone all resources on the list
    // 5a. Await the clone all resources flight and build a response
    final var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    addStep(new FindResourcesToCloneStep(flightBeanBag.getResourceDao()), RetryRules.cloud());

    addStep(new CreateIdsForFutureStepsStep());

    addStep(
        new LaunchCreateGcpContextFlightStep(flightBeanBag.getWorkspaceService()),
        RetryRules.cloud());
    addStep(new AwaitCreateGcpContextFlightStep(), RetryRules.cloudLongRunning());

    if (flightBeanBag.getFeatureConfiguration().isTpsEnabled()) {
      addStep(new ClonePolicyAttributesStep(flightBeanBag.getTpsApiDispatch()), RetryRules.cloud());
    }

    addStep(new LaunchCloneAllResourcesFlightStep(), RetryRules.cloud());
    addStep(new AwaitCloneAllResourcesFlightStep(), RetryRules.cloudLongRunning());
  }
}
