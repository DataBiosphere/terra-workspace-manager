package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;

public class CloneGcpWorkspaceFlight extends Flight {

  public CloneGcpWorkspaceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    // Flight Map
    // 0. Create a job id for create cloud context sub-flight
    // 1. Create destination workspace and cloud context
    // 2. Build a list of resources to clone
    // 3. Clone a resource on the list. Rerun until all resources are cloned.
    // 4. Build the response payload.
    final var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    addStep(new FindResourcesToCloneStep(flightBeanBag.getResourceDao()), RetryRules.cloud());
    addStep(new CreateIdsForFutureStepsStep());
//    addStep(new CreateWorkspaceStep(flightBeanBag.getWorkspaceDao()));
    addStep(new LaunchWorkspaceCreateFlightStep());
    addStep(new AwaitWorkspaceCreateFlightStep());
    addStep(
        new CreateDestinationCloudContextStep(flightBeanBag.getWorkspaceService()),
        RetryRules.cloud());
    addStep(
        new CloneEachResourceStep(
            flightBeanBag.getReferencedResourceService(),
            flightBeanBag.getControlledResourceService()),
        RetryRules.cloud());
  }
}
