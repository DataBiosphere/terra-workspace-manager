package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;

public class CloneControlledAzureDatabaseResourceFlight extends Flight {

  public CloneControlledAzureDatabaseResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    // TODO: This is only for testing purposes. Remove before merge
    System.out.println("**** FIND ME - inside CloneControlledAzureDatabaseResourceFlight ****");
  }
}
