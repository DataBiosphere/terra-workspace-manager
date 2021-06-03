package bio.terra.workspace.service.resource.controlled.flight.clone;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;

public class CloneControlledResourceFlight extends Flight {

  public CloneControlledResourceFlight(FlightMap inputParameters,
      Object applicationContext) {
    super(inputParameters, applicationContext);

    // Flight Plan
    // 1. Gather creation parameters from existing object
    // 2. Launch sub-flight to create appropriate resource
    // 3. Copy data across resources (future)
    // 4. Build result object
  }
}
