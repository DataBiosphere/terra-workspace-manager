package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;

/** Top-most flight for cloning a workspace. Launches sub-flights for most of the work. */
public class CloneWorkspaceFlight extends Flight {

  public CloneWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // NOTE: MergePolicyAttributesStep is not part of this flight,
    // it happens during workspace creation so auth domains can be added to workspace

    // Flight Plan
    // * Clone all folders in the workspace
    // * Build a list of resources to clone and attach the updated cloned folder id
    // * Create job IDs for future sub-flights and a couple other things
    // * Launch a flight to clone all resources on the list
    // * Await the clone all resources flight and build a response

    var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    var cloudRetryRule = RetryRules.cloud();
    var longCloudRetryRule = RetryRules.cloudLongRunning();

    addStep(
        new CloneAllFoldersStep(flightBeanBag.getSamService(), flightBeanBag.getFolderDao()),
        RetryRules.shortDatabase());

    addStep(new FindResourcesToCloneStep(flightBeanBag.getResourceDao()), cloudRetryRule);

    addStep(new CreateIdsForFutureStepsStep());

    addStep(new LaunchCloneAllResourcesFlightStep(), cloudRetryRule);
    addStep(new AwaitCloneAllResourcesFlightStep(), longCloudRetryRule);
  }
}
