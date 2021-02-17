package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;

public class CreateControlledResourceFlight extends Flight {

  public CreateControlledResourceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    // Step 0: Generate a new resource UUID
    addStep(new GenerateControlledResourceIdStep());
    // Step 1: store the resource metadata in CloudSQL
    addStep(new StoreControlledResourceMetadataStep(flightBeanBag.getControlledResourceDao()));

    // Step 2: create the bucket via CRL
    // TODO: pass this step in via input parameters

    // Step 3: create the Sam resource associated with the resource
    //
    // Step 4: assign custom roles to the bucket based on Sam policies
  }
}
