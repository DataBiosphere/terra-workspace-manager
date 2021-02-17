package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;

/**
 * Flight for creation of a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class CreateControlledResourceFlight extends Flight {

  public CreateControlledResourceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);

    // Step 0: Generate a new resource UUID
    addStep(new GenerateControlledResourceIdStep());

    // Step 1: store the resource metadata in CloudSQL
    addStep(new StoreControlledResourceMetadataStep(flightBeanBag.getControlledResourceDao()));

    // Step 2: create the cloud resource via CRL
    final Step createResourceStep =
        inputParameters.get(WorkspaceFlightMapKeys.CREATE_CLOUD_RESOURCE_STEP, Step.class);
    addStep(createResourceStep);

    // Step 3: create the Sam resource associated with the resource
    final Step createSamResourceStep =
        inputParameters.get(WorkspaceFlightMapKeys.CREATE_SAM_RESOURCE_STEP, Step.class);

    // Step 4: assign custom roles to the resource based on Sam policies
    // TODO: can this step be the same for all resource types?
  }
}
