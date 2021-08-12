package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;

public class CloneGcpWorkspaceFlight extends Flight {

  public CloneGcpWorkspaceFlight(FlightMap inputParameters,
      Object applicationContext) {
    super(inputParameters, applicationContext);
    // Flight Map
    // 0. Create a job id for create cloud context sub-flight
    // 1. Create destination workspace and cloud context
    // 2. Build a list of resources to clone
    // 3. Clone a resource on the list. Rerun until all resources are cloned.
    final var flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    addStep(new CreateJobIdForCreateCloudContextStep());
    addStep(new CreateDestinationCloudContextStep(flightBeanBag.getWorkspaceService()));
  }
}
